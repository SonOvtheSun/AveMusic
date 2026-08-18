package com.avemonica.avemusic.file.controller;

import com.avemonica.avemusic.common.web.ApiResult;
import com.avemonica.avemusic.file.config.FileStorageProperties;
import com.avemonica.avemusic.file.service.LocalFileStorageService;
import com.avemonica.avemusic.file.service.LocalFileStorageService.StoredFile;
import com.avemonica.avemusic.file.service.LocalFileStorageService.StorageCategory;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

@RestController
public final class FileUploadController {

    /**
     * 大于 5 MiB 的音频由前端切片。
     *
     * 每个普通分片固定 5 MiB，最后一个分片允许不足 5 MiB。
     */
    private static final long CHUNK_SIZE_BYTES =
            2L * 1024L * 1024L;

    /**
     * 分片上传可能持续较长时间，成功收到一个分片后刷新 ticket TTL。
     */
    private static final Duration CHUNK_TICKET_TTL =
            Duration.ofHours(2);

    /**
     * 超过这个时间的临时分片目录视为废弃上传。
     */
    private static final Duration STALE_CHUNK_AGE =
            Duration.ofHours(3);

    private static final String INTERNAL_TOKEN_HEADER =
            "X-Internal-Token";

    private static final String UPLOAD_TICKET_HEADER =
            "X-Upload-Ticket";

    private static final String TICKET_PREFIX =
            "avemusic:file:upload-ticket:";

    private final LocalFileStorageService storageService;
    private final FileStorageProperties properties;
    private final StringRedisTemplate redisTemplate;

    /**
     * 分片先写到系统临时目录，避免暴露在 /files/** 静态资源路径下。
     */
    private final Path chunkRoot =
            Path.of(
                    System.getProperty("java.io.tmpdir"),
                    "avemusic-upload-chunks"
            ).toAbsolutePath().normalize();

    /**
     * 防止同一个 ticket 被并发执行两次合并。
     *
     * AveMusic 当前 File Service 为单实例，这里使用进程内锁即可。
     */
    private final ConcurrentMap<String, Object>
            completeLocks = new ConcurrentHashMap<>();

    public FileUploadController(
            LocalFileStorageService storageService,
            FileStorageProperties properties,
            StringRedisTemplate redisTemplate
    ) {
        this.storageService = storageService;
        this.properties = properties;
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    void initializeChunkRoot() throws IOException {
        Files.createDirectories(chunkRoot);
        cleanupStaleChunkDirectories();
    }

    /**
     * 服务内部上传接口保持原样。
     */
    @PostMapping(
            value = "/internal/files/{category}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ApiResult<StoredFile> upload(
            @RequestHeader(INTERNAL_TOKEN_HEADER)
            String internalToken,

            @PathVariable
            String category,

            @RequestPart("file")
            MultipartFile file
    ) throws IOException {
        verifyInternalToken(internalToken);

        return ApiResult.success(
                storageService.store(
                        file,
                        StorageCategory.from(category)
                )
        );
    }

    /**
     * 小文件仍然一次上传。
     *
     * 对音频来说：<= 5 MiB 走这里；
     * > 5 MiB 时前端会改走 /chunk + /complete。
     */
    @PostMapping(
            value = "/upload/files/{category}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ApiResult<StoredFile> directUpload(
            @RequestHeader(UPLOAD_TICKET_HEADER)
            String ticket,

            @PathVariable
            String category,

            @RequestPart("file")
            MultipartFile file
    ) throws IOException {
        verifyDirectUploadTicket(
                ticket,
                category,
                file.getSize()
        );

        return ApiResult.success(
                storageService.store(
                        file,
                        StorageCategory.from(category)
                )
        );
    }

    /**
     * 音频分片上传。
     *
     * Request Body 不是 multipart，而是纯 application/octet-stream。
     * 这样一个 5 MiB 分片在网络上就是约 5 MiB 的原始字节流，
     * 不再额外套一层 multipart 文件体。
     *
     * chunkIndex 从 0 开始。
     */
    @PostMapping(
            value = "/upload/files/{category}/chunk",
            consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE
    )
    public ApiResult<Void> uploadChunk(
            @RequestHeader(UPLOAD_TICKET_HEADER)
            String ticket,

            @PathVariable
            String category,

            @RequestParam
            int chunkIndex,

            @RequestParam
            int chunkCount,

            HttpServletRequest request
    ) throws IOException {
        if (!"audio".equals(category)) {
            throw new IllegalArgumentException(
                    "只有音频文件支持分片上传"
            );
        }

        String normalizedTicket =
                normalizeTicket(ticket);

        UploadTicketInfo ticketInfo =
                verifyReusableUploadTicket(
                        normalizedTicket,
                        category
                );

        int expectedChunkCount =
                expectedChunkCount(
                        ticketInfo.totalSize()
                );

        if (chunkCount != expectedChunkCount) {
            throw new SecurityException(
                    "分片总数与上传凭证不匹配"
            );
        }

        if (chunkIndex < 0
                || chunkIndex >= chunkCount) {
            throw new IllegalArgumentException(
                    "分片序号不合法"
            );
        }

        long expectedChunkSize =
                expectedChunkSize(
                        ticketInfo.totalSize(),
                        chunkIndex
                );

        if (chunkIndex == 0) {
            cleanupStaleChunkDirectories();
        }

        Path sessionDirectory =
                resolveChunkDirectory(
                        normalizedTicket
                );

        Files.createDirectories(
                sessionDirectory
        );

        Path target =
                resolveChunkFile(
                        sessionDirectory,
                        chunkIndex
                );

        /*
         * 先写临时文件，再原子替换正式分片。
         *
         * 如果网络中途断掉，不会留下一个看起来“已经上传成功”
         * 但实际只有半截内容的 chunk-xxxxxx.part。
         */
        Path temporary =
                Files.createTempFile(
                        sessionDirectory,
                        "upload-",
                        ".tmp"
                );

        try {
            long written =
                    copyChunkWithLimit(
                            request.getInputStream(),
                            temporary,
                            CHUNK_SIZE_BYTES
                    );

            if (written != expectedChunkSize) {
                throw new SecurityException(
                        "分片大小不正确，chunkIndex="
                                + chunkIndex
                                + ", expected="
                                + expectedChunkSize
                                + ", actual="
                                + written
                );
            }

            Files.move(
                    temporary,
                    target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );

        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(
                    temporary,
                    target,
                    StandardCopyOption.REPLACE_EXISTING
            );

        } finally {
            Files.deleteIfExists(
                    temporary
            );
        }

        /*
         * 上传仍在进行时刷新 ticket，避免较慢网络下 ticket 中途过期。
         */
        redisTemplate.expire(
                TICKET_PREFIX + normalizedTicket,
                CHUNK_TICKET_TTL
        );

        return ApiResult.success();
    }

    /**
     * 所有分片上传完成后：
     *
     * 1. 校验所有分片是否齐全；
     * 2. 按 chunkIndex 顺序拼成一个完整临时音频；
     * 3. 校验最终总大小；
     * 4. 调用 LocalFileStorageService 转存到正式目录；
     * 5. 删除 ticket 与临时分片。
     */
    @PostMapping(
            value = "/upload/files/{category}/complete",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public ApiResult<StoredFile> completeChunkUpload(
            @RequestHeader(UPLOAD_TICKET_HEADER)
            String ticket,

            @PathVariable
            String category,

            @RequestBody
            CompleteChunkUploadRequest body
    ) throws IOException {
        if (!"audio".equals(category)) {
            throw new IllegalArgumentException(
                    "只有音频文件支持分片上传"
            );
        }

        if (body == null
                || body.originalName() == null
                || body.originalName().isBlank()) {
            throw new IllegalArgumentException(
                    "原始文件名不能为空"
            );
        }

        String normalizedTicket =
                normalizeTicket(ticket);

        Object lock =
                completeLocks.computeIfAbsent(
                        normalizedTicket,
                        ignored -> new Object()
                );

        synchronized (lock) {
            try {
                UploadTicketInfo ticketInfo =
                        verifyReusableUploadTicket(
                                normalizedTicket,
                                category
                        );

                int expectedChunkCount =
                        expectedChunkCount(
                                ticketInfo.totalSize()
                        );

                if (body.chunkCount()
                        != expectedChunkCount) {
                    throw new SecurityException(
                            "分片总数与上传凭证不匹配"
                    );
                }

                Path sessionDirectory =
                        resolveChunkDirectory(
                                normalizedTicket
                        );

                if (!Files.isDirectory(
                        sessionDirectory
                )) {
                    throw new IllegalStateException(
                            "未找到分片上传数据"
                    );
                }

                verifyAllChunks(
                        sessionDirectory,
                        ticketInfo.totalSize(),
                        expectedChunkCount
                );

                Path mergedFile =
                        Files.createTempFile(
                                sessionDirectory,
                                "merged-",
                                ".tmp"
                        );

                try {
                    mergeChunks(
                            sessionDirectory,
                            mergedFile,
                            expectedChunkCount
                    );

                    long mergedSize =
                            Files.size(
                                    mergedFile
                            );

                    if (mergedSize
                            != ticketInfo.totalSize()) {
                        throw new IllegalStateException(
                                "合并后的文件大小不正确，expected="
                                        + ticketInfo.totalSize()
                                        + ", actual="
                                        + mergedSize
                        );
                    }

                    StoredFile storedFile =
                            storageService.store(
                                    mergedFile,
                                    body.originalName(),
                                    body.contentType(),
                                    StorageCategory.AUDIO
                            );

                    /*
                     * 只有最终文件真正转存成功以后，才消费 ticket。
                     */
                    redisTemplate.delete(
                            TICKET_PREFIX
                                    + normalizedTicket
                    );

                    deleteDirectoryQuietly(
                            sessionDirectory
                    );

                    return ApiResult.success(
                            storedFile
                    );

                } finally {
                    Files.deleteIfExists(
                            mergedFile
                    );
                }

            } finally {
                completeLocks.remove(
                        normalizedTicket,
                        lock
                );
            }
        }
    }

    /**
     * 一次性直传：读取并删除 ticket。
     */
    private void verifyDirectUploadTicket(
            String ticket,
            String category,
            long actualSize
    ) {
        String normalizedTicket =
                normalizeTicket(ticket);

        UploadTicketInfo info =
                readUploadTicket(
                        normalizedTicket,
                        true
                );

        verifyTicketCategory(
                info,
                category
        );

        if (actualSize != info.totalSize()) {
            throw new SecurityException(
                    "文件大小与上传凭证不匹配"
            );
        }
    }

    /**
     * 分片上传：上传完成前 ticket 不能删除，因此这里只读取。
     */
    private UploadTicketInfo
    verifyReusableUploadTicket(
            String normalizedTicket,
            String category
    ) {
        UploadTicketInfo info =
                readUploadTicket(
                        normalizedTicket,
                        false
                );

        verifyTicketCategory(
                info,
                category
        );

        return info;
    }

    private UploadTicketInfo readUploadTicket(
            String normalizedTicket,
            boolean consume
    ) {
        String key =
                TICKET_PREFIX
                        + normalizedTicket;

        String value = consume
                ? redisTemplate
                .opsForValue()
                .getAndDelete(key)
                : redisTemplate
                .opsForValue()
                .get(key);

        if (value == null) {
            throw new SecurityException(
                    "上传凭证无效或已经过期"
            );
        }

        String[] parts =
                value.split(
                        "\\|",
                        3
                );

        if (parts.length != 3) {
            throw new SecurityException(
                    "上传凭证格式错误"
            );
        }

        long totalSize;

        try {
            totalSize =
                    Long.parseLong(
                            parts[2]
                    );
        } catch (NumberFormatException exception) {
            throw new SecurityException(
                    "上传凭证格式错误"
            );
        }

        if (totalSize <= 0) {
            throw new SecurityException(
                    "上传凭证中的文件大小不合法"
            );
        }

        return new UploadTicketInfo(
                parts[0],
                parts[1],
                totalSize
        );
    }

    private static void verifyTicketCategory(
            UploadTicketInfo info,
            String category
    ) {
        if (!category.equals(
                info.category()
        )) {
            throw new SecurityException(
                    "文件分类与上传凭证不匹配"
            );
        }
    }

    private static int expectedChunkCount(
            long totalSize
    ) {
        return Math.toIntExact(
                (totalSize
                        + CHUNK_SIZE_BYTES
                        - 1)
                        / CHUNK_SIZE_BYTES
        );
    }

    private static long expectedChunkSize(
            long totalSize,
            int chunkIndex
    ) {
        long start =
                Math.multiplyExact(
                        (long) chunkIndex,
                        CHUNK_SIZE_BYTES
                );

        long remaining =
                totalSize - start;

        if (remaining <= 0) {
            throw new IllegalArgumentException(
                    "分片序号超出文件范围"
            );
        }

        return Math.min(
                CHUNK_SIZE_BYTES,
                remaining
        );
    }

    private Path resolveChunkDirectory(
            String normalizedTicket
    ) {
        Path directory =
                chunkRoot
                        .resolve(normalizedTicket)
                        .normalize();

        if (!directory.startsWith(
                chunkRoot
        )) {
            throw new SecurityException(
                    "非法上传凭证"
            );
        }

        return directory;
    }

    private static Path resolveChunkFile(
            Path sessionDirectory,
            int chunkIndex
    ) {
        Path file =
                sessionDirectory
                        .resolve(
                                String.format(
                                        "chunk-%06d.part",
                                        chunkIndex
                                )
                        )
                        .normalize();

        if (!file.startsWith(
                sessionDirectory
        )) {
            throw new SecurityException(
                    "非法分片路径"
            );
        }

        return file;
    }

    private static long copyChunkWithLimit(
            InputStream inputStream,
            Path target,
            long maxBytes
    ) throws IOException {
        long total = 0;
        byte[] buffer =
                new byte[64 * 1024];

        try (InputStream input = inputStream;
             OutputStream output =
                     Files.newOutputStream(
                             target,
                             StandardOpenOption.WRITE,
                             StandardOpenOption.TRUNCATE_EXISTING
                     )) {

            int read;

            while ((read = input.read(buffer)) != -1) {
                total += read;

                if (total > maxBytes) {
                    throw new IllegalArgumentException(
                            "单个分片不能超过5MB"
                    );
                }

                output.write(
                        buffer,
                        0,
                        read
                );
            }
        }

        return total;
    }

    private static void verifyAllChunks(
            Path sessionDirectory,
            long totalSize,
            int chunkCount
    ) throws IOException {
        for (int index = 0;
             index < chunkCount;
             index++) {

            Path chunk =
                    resolveChunkFile(
                            sessionDirectory,
                            index
                    );

            if (!Files.isRegularFile(
                    chunk
            )) {
                throw new IllegalStateException(
                        "缺少分片："
                                + index
                );
            }

            long expected =
                    expectedChunkSize(
                            totalSize,
                            index
                    );

            long actual =
                    Files.size(chunk);

            if (actual != expected) {
                throw new IllegalStateException(
                        "分片大小异常，chunkIndex="
                                + index
                                + ", expected="
                                + expected
                                + ", actual="
                                + actual
                );
            }
        }
    }

    private static void mergeChunks(
            Path sessionDirectory,
            Path mergedFile,
            int chunkCount
    ) throws IOException {
        try (OutputStream output =
                     Files.newOutputStream(
                             mergedFile,
                             StandardOpenOption.WRITE,
                             StandardOpenOption.TRUNCATE_EXISTING
                     )) {

            for (int index = 0;
                 index < chunkCount;
                 index++) {

                Path chunk =
                        resolveChunkFile(
                                sessionDirectory,
                                index
                        );

                try (InputStream input =
                             Files.newInputStream(
                                     chunk
                             )) {
                    input.transferTo(
                            output
                    );
                }
            }
        }
    }

    private static String normalizeTicket(
            String ticket
    ) {
        if (ticket == null
                || ticket.isBlank()) {
            throw new SecurityException(
                    "缺少上传凭证"
            );
        }

        try {
            return UUID.fromString(
                    ticket.trim()
            ).toString();

        } catch (IllegalArgumentException exception) {
            throw new SecurityException(
                    "上传凭证格式错误"
            );
        }
    }

    private void cleanupStaleChunkDirectories() {
        if (!Files.isDirectory(
                chunkRoot
        )) {
            return;
        }

        long cutoff =
                System.currentTimeMillis()
                        - STALE_CHUNK_AGE
                        .toMillis();

        try (Stream<Path> paths =
                     Files.list(
                             chunkRoot
                     )) {

            paths
                    .filter(Files::isDirectory)
                    .filter(path -> {
                        try {
                            return Files
                                    .getLastModifiedTime(path)
                                    .toMillis()
                                    < cutoff;
                        } catch (IOException exception) {
                            return false;
                        }
                    })
                    .forEach(
                            this::deleteDirectoryQuietly
                    );

        } catch (IOException exception) {
            System.err.println(
                    "[File-Chunk] 清理过期分片失败："
                            + exception.getMessage()
            );
        }
    }

    private void deleteDirectoryQuietly(
            Path directory
    ) {
        if (directory == null
                || !Files.exists(directory)) {
            return;
        }

        try (Stream<Path> paths =
                     Files.walk(directory)) {

            paths
                    .sorted(
                            Comparator.reverseOrder()
                    )
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException exception) {
                            System.err.println(
                                    "[File-Chunk] 删除临时文件失败："
                                            + path
                            );
                        }
                    });

        } catch (IOException exception) {
            System.err.println(
                    "[File-Chunk] 清理上传目录失败："
                            + exception.getMessage()
            );
        }
    }

    private void verifyInternalToken(
            String actualToken
    ) {
        byte[] expected =
                properties.internalToken()
                        .getBytes(
                                StandardCharsets.UTF_8
                        );

        byte[] actual =
                actualToken.getBytes(
                        StandardCharsets.UTF_8
                );

        if (!MessageDigest.isEqual(
                expected,
                actual
        )) {
            throw new SecurityException(
                    "无权调用文件服务"
            );
        }
    }

    private record UploadTicketInfo(
            String category,
            String userId,
            long totalSize
    ) {
    }

    public record CompleteChunkUploadRequest(
            String originalName,
            String contentType,
            int chunkCount
    ) {
    }
}