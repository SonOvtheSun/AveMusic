package com.avemonica.avemusic.file.exception;

import com.avemonica.avemusic.common.web.ApiResult;
import com.avemonica.avemusic.file.controller.FileUploadController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.io.IOException;

/**
 * 只处理 FileUploadController 的上传接口异常。
 *
 * 不要做成全局 @RestControllerAdvice：
 * /files/** 是 Spring MVC ResourceHttpRequestHandler 负责输出的静态音频/图片。
 * 浏览器播放、拖动进度或切歌时可能取消/重建 Range 请求，
 * 底层可能产生 IOException。
 *
 * 如果全局捕获 IOException，再返回 ApiResult JSON，
 * 此时响应可能已经被设置为 audio/flac、audio/mpeg 等 Content-Type，
 * Spring 就会尝试在 audio/flac 响应中序列化 ApiResult，
 * 最终触发 HttpMessageNotWritableException。
 */
@RestControllerAdvice(
        assignableTypes = FileUploadController.class
)
public final class FileExceptionHandler {

    @ExceptionHandler(
            IllegalArgumentException.class
    )
    public ResponseEntity<ApiResult<Void>>
    handleBadRequest(
            IllegalArgumentException exception
    ) {
        return ResponseEntity
                .badRequest()
                .body(
                        ApiResult.failure(
                                "FILE-400",
                                exception.getMessage()
                        )
                );
    }

    @ExceptionHandler(
            SecurityException.class
    )
    public ResponseEntity<ApiResult<Void>>
    handleForbidden(
            SecurityException exception
    ) {
        return ResponseEntity
                .status(
                        HttpStatus.FORBIDDEN
                )
                .body(
                        ApiResult.failure(
                                "FILE-403",
                                exception.getMessage()
                        )
                );
    }

    @ExceptionHandler(
            MaxUploadSizeExceededException.class
    )
    public ResponseEntity<ApiResult<Void>>
    handleTooLarge() {
        return ResponseEntity
                .status(
                        HttpStatus.PAYLOAD_TOO_LARGE
                )
                .body(
                        ApiResult.failure(
                                "FILE-413",
                                "上传文件超过系统允许的最大大小"
                        )
                );
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<ApiResult<Void>>
    handleStorageFailure() {
        return ResponseEntity
                .status(
                        HttpStatus.INTERNAL_SERVER_ERROR
                )
                .body(
                        ApiResult.failure(
                                "FILE-500",
                                "文件保存失败"
                        )
                );
    }
}