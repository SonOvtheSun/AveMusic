package com.avemonica.avemusic.user.api.request;

public record UserLoginRequest(
        String username,
        String password
) {
}