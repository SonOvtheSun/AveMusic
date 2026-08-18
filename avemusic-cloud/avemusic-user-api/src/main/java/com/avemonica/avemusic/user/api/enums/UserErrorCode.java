package com.avemonica.avemusic.user.api.enums;

import com.avemonica.minirpc.core.error.RpcErrorCode;

public enum UserErrorCode implements RpcErrorCode {

    USERNAME_ALREADY_EXISTS(
            "USR-1001",
            "用户名已存在"
    ),

    PHONE_ALREADY_EXISTS(
            "USR-1002",
            "手机号已注册"
    ),

    INVALID_PARAMETER(
            "USR-1003",
            "请求参数不正确"
    ),

    INVALID_CREDENTIALS(
            "AUTH-1001",
            "用户名或密码错误"
    ),

    INVALID_SMS_CODE(
            "AUTH-1002",
            "验证码错误或已经过期"
    ),

    SMS_TOO_FREQUENT(
            "AUTH-1003",
            "验证码发送过于频繁，请稍后再试"
    ),

    PHONE_NOT_REGISTERED(
            "AUTH-1004",
            "该手机号尚未注册"
    ),

    USER_DISABLED(
            "AUTH-1005",
            "该账号已被禁用"
    ),

    FORBIDDEN(
            "AUTH-1006",
            "禁止"
    ),

    USER_NOT_FOUND(
            "AUTH-1007",
                    "未找到账号"
    );



    private final String code;
    private final String defaultMessage;

    UserErrorCode(
            String code,
            String defaultMessage
    ) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}