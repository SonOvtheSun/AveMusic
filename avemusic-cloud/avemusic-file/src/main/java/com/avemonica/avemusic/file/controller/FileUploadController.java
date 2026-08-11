package com.avemonica.avemusic.file.controller;

import com.avemonica.avemusic.common.web.ApiResult;

import com.avemonica.avemusic.file.config.FileStorageProperties;
import com.avemonica.avemusic.file.service.LocalFileStorageService;
import com.avemonica.avemusic.file.service.LocalFileStorageService.StoredFile;
import com.avemonica.avemusic.file.service.LocalFileStorageService.StorageCategory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
public final class FileUploadController {

    private static final String INTERNAL_TOKEN_HEADER =
            "X-Internal-Token";

    private final LocalFileStorageService storageService;
    private final FileStorageProperties properties;

    private static final String TICKET_PREFIX =
            "avemusic:file:upload-ticket:";

    private final StringRedisTemplate redisTemplate;

    public FileUploadController(
            LocalFileStorageService storageService,
            FileStorageProperties properties,
            StringRedisTemplate redisTemplate
    ) {
        this.storageService = storageService;

        this.properties = properties;

        this.redisTemplate = redisTemplate;
    }

    @PostMapping(
            value = "/internal/files/{category}",
            consumes =
                    MediaType.MULTIPART_FORM_DATA_VALUE
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

    @PostMapping(
            value = "/upload/files/{category}",
            consumes =
                    MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ApiResult<StoredFile>
    directUpload(
            @RequestHeader(
                    "X-Upload-Ticket"
            )
            String ticket,

            @PathVariable
            String category,

            @RequestPart("file")
            MultipartFile file
    ) throws IOException {

        verifyUploadTicket(
                ticket,
                category,
                file
        );

        return ApiResult.success(
                storageService.store(
                        file,
                        StorageCategory.from(
                                category
                        )
                )
        );
    }

    private void verifyUploadTicket(
            String ticket,
            String category,
            MultipartFile file
    ) {
        if (
                ticket == null
                        || ticket.isBlank()
        ) {
            throw new SecurityException(
                    "缺少上传凭证"
            );
        }

        String key =
                TICKET_PREFIX
                        + ticket;

        /*
         * 原子：
         * 获取 ticket
         * +
         * 删除 ticket
         *
         * 因此一个 ticket 只能使用一次。
         */
        String value =
                redisTemplate
                        .opsForValue()
                        .getAndDelete(key);

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

        String expectedCategory =
                parts[0];

        long expectedSize;

        try {
            expectedSize =
                    Long.parseLong(
                            parts[2]
                    );
        } catch (
                NumberFormatException exception
        ) {
            throw new SecurityException(
                    "上传凭证格式错误"
            );
        }

        if (
                !category.equals(
                        expectedCategory
                )
        ) {
            throw new SecurityException(
                    "文件分类与上传凭证不匹配"
            );
        }

        if (
                file.getSize()
                        != expectedSize
        ) {
            throw new SecurityException(
                    "文件大小与上传凭证不匹配"
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
}
