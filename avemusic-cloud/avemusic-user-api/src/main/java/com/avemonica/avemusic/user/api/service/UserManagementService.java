package com.avemonica.avemusic.user.api.service;

import com.avemonica.avemusic.user.api.dto.UserManagementModels.UserItem;

import java.util.List;

public interface UserManagementService {

    List<UserItem> listUsers();
}
