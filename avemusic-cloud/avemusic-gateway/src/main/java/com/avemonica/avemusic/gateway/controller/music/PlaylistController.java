package com.avemonica.avemusic.gateway.controller.music;

import com.avemonica.avemusic.common.web.ApiResult;
import com.avemonica.avemusic.music.api.dto.PlaylistModels.AddSongRequest;
import com.avemonica.avemusic.music.api.dto.PlaylistModels.CreatePlaylistRequest;
import com.avemonica.avemusic.music.api.dto.PlaylistModels.PlaylistDetail;
import com.avemonica.avemusic.music.api.dto.PlaylistModels.PlaylistSummary;
import com.avemonica.avemusic.music.api.dto.PlaylistModels.RemoveSongRequest;
import com.avemonica.avemusic.music.api.dto.PlaylistModels.UpdatePlaylistRequest;
import com.avemonica.avemusic.music.api.service.PlaylistService;
import com.avemonica.avemusic.music.api.dto
        .PlaylistModels.PlaylistPage;
import com.avemonica.minirpc.spring.annotation.MiniRpcReference;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/playlists")
public class PlaylistController {

    @MiniRpcReference(
            host = "127.0.0.1",
            port = 20882,
            group = "music",
            version = "1.0.0"
    )
    private PlaylistService playlistService;

    @GetMapping("/mine")
    public ApiResult<List<PlaylistSummary>>
    mine(
            Authentication authentication
    ) {
        return ApiResult.success(
                playlistService.listMine(
                        authentication.getName()
                )
        );
    }

    /**
     * “我的音乐 -> 收藏的歌单”。
     */
    @GetMapping("/favorites")
    public ApiResult<List<PlaylistSummary>>
    favorites(
            Authentication authentication
    ) {
        return ApiResult.success(
                playlistService.listFavorites(
                        authentication.getName()
                )
        );
    }

    /**
     * 自己歌单：
     * PUBLIC / PRIVATE 均可查看。
     *
     * 别人歌单：
     * 只有 PUBLIC 才可查看。
     */
    @GetMapping("/{playlistId}")
    public ApiResult<PlaylistDetail>
    detail(
            @PathVariable
            String playlistId,

            Authentication authentication
    ) {
        return ApiResult.success(
                playlistService
                        .getDetail(
                                authentication
                                        .getName(),
                                playlistId
                        )
        );
    }

    @PostMapping
    public ApiResult<PlaylistSummary>
    create(
            @Valid
            @RequestBody
            PlaylistBody body,

            Authentication authentication
    ) {
        return ApiResult.success(
                playlistService
                        .createPlaylist(
                                new CreatePlaylistRequest(
                                        authentication
                                                .getName(),
                                        body.name(),
                                        body.introduction(),
                                        body.coverUrl(),
                                        body.visibility()
                                )
                        )
        );
    }

    @PutMapping("/{playlistId}")
    public ApiResult<PlaylistSummary>
    update(
            @PathVariable
            String playlistId,

            @Valid
            @RequestBody
            PlaylistBody body,

            Authentication authentication
    ) {
        return ApiResult.success(
                playlistService
                        .updatePlaylist(
                                new UpdatePlaylistRequest(
                                        authentication
                                                .getName(),
                                        playlistId,
                                        body.name(),
                                        body.introduction(),
                                        body.coverUrl(),
                                        body.visibility()
                                )
                        )
        );
    }

    @PostMapping(
            "/{playlistId}/songs/{songId}"
    )
    public ApiResult<Void> addSong(
            @PathVariable
            String playlistId,

            @PathVariable
            String songId,

            Authentication authentication
    ) {
        playlistService.addSong(
                new AddSongRequest(
                        authentication.getName(),
                        playlistId,
                        songId
                )
        );

        return ApiResult.success();
    }

    @GetMapping("/ranking")
    public ApiResult<PlaylistPage>
    ranking(
            @RequestParam(
                    defaultValue = "1"
            )
            @Min(1)
            @Max(20)
            int page
    ) {
        return ApiResult.success(
                playlistService
                        .pagePopularPlaylists(
                                page
                        )
        );
    }

    @DeleteMapping(
            "/{playlistId}/songs/{songId}"
    )
    public ApiResult<Void> removeSong(
            @PathVariable
            String playlistId,

            @PathVariable
            String songId,

            Authentication authentication
    ) {
        playlistService.removeSong(
                new RemoveSongRequest(
                        authentication.getName(),
                        playlistId,
                        songId
                )
        );

        return ApiResult.success();
    }

    /**
     * 收藏别人公开歌单。
     */
    @PostMapping(
            "/{playlistId}/favorite"
    )
    public ApiResult<Void> favorite(
            @PathVariable
            String playlistId,

            Authentication authentication
    ) {
        playlistService.favoritePlaylist(
                authentication.getName(),
                playlistId
        );

        return ApiResult.success();
    }

    /**
     * 取消收藏。
     */
    @DeleteMapping(
            "/{playlistId}/favorite"
    )
    public ApiResult<Void> unfavorite(
            @PathVariable
            String playlistId,

            Authentication authentication
    ) {
        playlistService.unfavoritePlaylist(
                authentication.getName(),
                playlistId
        );

        return ApiResult.success();
    }

    public record PlaylistBody(
            @NotBlank(
                    message = "歌单名称不能为空"
            )
            @Size(
                    max = 128,
                    message = "歌单名称不能超过128个字符"
            )
            String name,

            @Size(
                    max = 1000,
                    message = "歌单简介不能超过1000个字符"
            )
            String introduction,

            @Size(
                    max = 512,
                    message = "歌单封面URL过长"
            )
            String coverUrl,

            @Pattern(
                    regexp = "PUBLIC|PRIVATE",
                    message = "歌单类型只能是PUBLIC或PRIVATE"
            )
            String visibility
    ) {
    }
}
