package com.avemonica.avemusic.gateway.exception;

import com.avemonica.avemusic.common.web.ApiResult;
import com.avemonica.minirpc.core.exception.RemoteBusinessException;
import com.avemonica.minirpc.core.exception.RemoteInvocationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public final class GlobalExceptionHandler {

    private static final System.Logger LOGGER =
            System.getLogger(
                    GlobalExceptionHandler.class.getName()
            );

    /**
     * Provider 明确返回的业务异常。
     */
    @ExceptionHandler(RemoteBusinessException.class)
    public ResponseEntity<ApiResult<Void>> handleBusinessException(
            RemoteBusinessException exception
    ) {
        HttpStatus status =
                businessStatus(exception.errorCode());

        return ResponseEntity
                .status(status)
                .body(
                        ApiResult.failure(
                                exception.errorCode(),
                                exception.getMessage()
                        )
                );
    }

    /**
     * Refresh Token 等 Gateway 本地认证失败。
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResult<Void>> handleBadCredentials(
            BadCredentialsException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(
                        ApiResult.failure(
                                "AUTH-401",
                                exception.getMessage()
                        )
                );
    }

    /**
     * 服务不存在、RPC 调用失败、Provider 系统故障等。
     * 不把远端内部错误暴露给前端。
     */
    @ExceptionHandler(RemoteInvocationException.class)
    public ResponseEntity<ApiResult<Void>> handleRemoteInvocation(
            RemoteInvocationException exception
    ) {
        LOGGER.log(
                System.Logger.Level.ERROR,
                "Remote RPC invocation failed",
                exception
        );

        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(
                        ApiResult.failure(
                                "GATEWAY-5001",
                                "后端服务暂时不可用"
                        )
                );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Void>> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        String message = exception
                .getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("请求参数错误");

        return ResponseEntity
                .badRequest()
                .body(
                        ApiResult.failure(
                                "REQUEST-400",
                                message
                        )
                );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleUnknown(
            Exception exception
    ) {
        LOGGER.log(
                System.Logger.Level.ERROR,
                "Unhandled Gateway exception",
                exception
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(
                        ApiResult.failure(
                                "GATEWAY-5000",
                                "系统内部错误"
                        )
                );
    }

    private static HttpStatus businessStatus(
            String errorCode
    ) {
        return switch (errorCode) {
            case "AUTH-1001" -> HttpStatus.UNAUTHORIZED;
            case "USR-1001" -> HttpStatus.CONFLICT;
            case "USR-1002" -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}