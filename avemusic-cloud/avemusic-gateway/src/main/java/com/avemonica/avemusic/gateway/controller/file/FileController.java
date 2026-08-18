package com.avemonica.avemusic.gateway.controller.file;

import com.avemonica.avemusic.common.web.ApiResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
public final class FileController {

    private static final Set<String>
            MANAGED_FILE_AUTHORITIES = Set.of(
            "sys::admin",
            "song::manage",
            "song::add"
    );

    private static final String TICKET_PREFIX =
            "avemusic:file:upload-ticket:";

    private static final long AUDIO_CHUNK_THRESHOLD_BYTES =
            2L * 1024L * 1024L;

    private final StringRedisTemplate redisTemplate;

    private final String publicUploadBaseUrl;

    public FileController(
            StringRedisTemplate redisTemplate,

            @Value(
                    "${avemusic.file-service.public-upload-base-url}"
            )
            String publicUploadBaseUrl
    ) {
        this.redisTemplate =
                redisTemplate;

        this.publicUploadBaseUrl =
                publicUploadBaseUrl.endsWith("/")
                        ? publicUploadBaseUrl.substring(
                        0,
                        publicUploadBaseUrl.length() - 1
                )
                        : publicUploadBaseUrl;
    }

    @PostMapping("/upload-ticket")
    public ApiResult<UploadTicketResponse>
    createUploadTicket(
            @RequestBody
            UploadTicketRequest body,
            Authentication authentication
    ) {
        String category =
                body.category();

        validateCategoryAndSize(
                category,
                body.size()
        );

        validatePermission(
                category,
                authentication
        );

        String ticket =
                UUID.randomUUID()
                        .toString();

        String key =
                TICKET_PREFIX
                        + ticket;

        /*
         * 格式：
         *
         * category | userId | size
         */
        String value =
                category
                        + "|"
                        + authentication.getName()
                        + "|"
                        + body.size();

        /*
         * 大音频会拆成多个 5 MiB 分片逐个上传。
         * 300 MB 文件在较慢公网环境下可能持续几十分钟，
         * 因此分片上传 ticket 不能再只有 60 秒。
         */
        Duration ticketTtl =
                "audio".equals(category)
                        && body.size()
                        > AUDIO_CHUNK_THRESHOLD_BYTES
                        ? Duration.ofHours(2)
                        : Duration.ofMinutes(5);

        redisTemplate
                .opsForValue()
                .set(
                        key,
                        value,
                        ticketTtl
                );

        String uploadUrl =
                "/upload/files/"
                        + category;

        return ApiResult.success(
                new UploadTicketResponse(
                        ticket,
                        uploadUrl
                )
        );
    }

    private static void validateUploadPermission(
            String category,
            Authentication authentication
    ) {
        /*
         * 头像允许任意已登录用户上传。
         */
        if ("avatar".equals(category)) {
            return;
        }

        /*
         * 音乐、专辑封面只允许后台管理者或音乐人上传。
         */
        if ("audio".equals(category)
                || "album-cover".equals(
                category
        )) {
            boolean allowed =
                    authentication != null
                            && authentication
                            .getAuthorities()
                            .stream()
                            .anyMatch(authority ->
                                    MANAGED_FILE_AUTHORITIES
                                            .contains(
                                                    authority
                                                            .getAuthority()
                                            )
                            );

            if (!allowed) {
                throw new AccessDeniedException(
                        "无权上传音乐或封面文件"
                );
            }

            return;
        }

        throw new IllegalArgumentException(
                "不支持的文件分类"
        );
    }

    private static void validateCategoryAndSize(
            String category,
            long size
    ) {
        if (category == null) {
            throw new IllegalArgumentException(
                    "文件分类不能为空"
            );
        }

        long maxBytes =
                switch (category) {

                    case "avatar",
                         "album-cover" ->
                            10L
                                    * 1024L
                                    * 1024L;

                    case "audio" ->
                            300L
                                    * 1024L
                                    * 1024L;

                    default ->
                            throw new IllegalArgumentException(
                                    "文件分类只能是："
                                            + "avatar、"
                                            + "album-cover、"
                                            + "audio"
                            );
                };

        if (
                size <= 0
                        || size > maxBytes
        ) {
            throw new IllegalArgumentException(
                    "文件大小不合法"
            );
        }
    }

    private static void validatePermission(
            String category,
            Authentication authentication
    ) {
        /*
         * avatar：
         * 普通登录用户也需要上传自己的头像。
         */
        if ("avatar".equals(category)) {
            return;
        }

        boolean allowed =
                authentication
                        .getAuthorities()
                        .stream()
                        .map(authority ->
                                authority.getAuthority()
                        )
                        .anyMatch(authority ->
                                "sys::admin"
                                        .equals(authority)
                                        || "song::manage"
                                        .equals(authority)
                                        || "song::add"
                                        .equals(authority)
                        );

        if (!allowed) {
            throw new AccessDeniedException(
                    "没有上传音乐文件的权限"
            );
        }
    }

    public record UploadTicketRequest(
            String category,
            long size
    ) {
    }

    public record UploadTicketResponse(
            String ticket,
            String uploadUrl
    ) {
    }
}