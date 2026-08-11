package com.avemonica.avemusic.user.api.service;

import com.avemonica.avemusic.user.api.dto.AuthModels.AuthUser;
import com.avemonica.avemusic.user.api.dto.AuthModels.PasswordLoginRequest;
import com.avemonica.avemusic.user.api.dto.AuthModels.PhoneLoginRequest;
import com.avemonica.avemusic.user.api.dto.AuthModels.RegisterRequest;
import com.avemonica.avemusic.user.api.dto.AuthModels.SendSmsCodeRequest;

public interface UserService {

    void sendSmsCode(SendSmsCodeRequest request);

    AuthUser register(RegisterRequest request);

    AuthUser authenticate(PasswordLoginRequest request);

    AuthUser loginByPhone(PhoneLoginRequest request);
}