package com.avemonica.avemusic.common.security;

public enum UserRole {

    SUPER_ADMIN("超级管理员"),
    OPERATOR("运维"),
    REVIEWER("审核员"),
    ARTIST("音乐人"),
    USER("普通用户");

    private final String displayName;

    UserRole(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public String authority() {
        return "ROLE_" + name();
    }
}
