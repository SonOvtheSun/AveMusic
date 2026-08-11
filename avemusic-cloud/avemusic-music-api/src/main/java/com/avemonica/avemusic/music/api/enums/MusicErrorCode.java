package com.avemonica.avemusic.music.api.enums;

import com.avemonica.minirpc.core.error.RpcErrorCode;

public enum MusicErrorCode
        implements RpcErrorCode {

    INVALID_PARAMETER(
            "MUSIC-1001",
            "音乐参数不正确"
    ),

    SONG_NOT_FOUND(
            "MUSIC-1002",
            "音乐不存在"
    ),

    ALBUM_NOT_FOUND(
            "MUSIC-1003",
            "专辑不存在或尚未审核通过"
    ),

    ARTIST_NOT_FOUND(
            "MUSIC-1004",
            "音乐人不存在或尚未审核通过"
    ),

    PERMISSION_DENIED(
            "MUSIC-1005",
            "无权执行该音乐管理操作"
    ),

    INVALID_REVIEW_STATE(
            "MUSIC-1006",
            "当前内容不能进行该审核操作"
    ),

    ARTIST_NAME_EXISTS(
            "MUSIC-1007",
            "音乐人名称已存在"
    ),

    DEPENDENCY_NOT_APPROVED(
            "MUSIC-1008",
            "关联的音乐人或专辑尚未审核通过"
    ),

    ARTIST_IN_USE(
            "MUSIC-1009",
            "音乐人仍绑定用户、歌曲或专辑，不能直接删除"
    ),

    ARTIST_NOT_APPROVED(
            "MUSIC-1010",
            "只有审核通过的音乐人才能上架"
    );

    private final String code;
    private final String defaultMessage;

    MusicErrorCode(
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
