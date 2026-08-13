package com.avemonica.avemusic.gateway.controller.music;

import com.avemonica.avemusic.common.web.ApiResult;
import com.avemonica.avemusic.gateway.security.ManagementActorResolver;
import com.avemonica.avemusic.gateway.service.PlaySessionService;
import com.avemonica.avemusic.music.api.dto.LyricsModels.LyricsResult;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.BatchDeleteRequest;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.CreateSongRequest;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.ReviewRequest;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.SongItem;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.UpdateSongRequest;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.PageResult;
import com.avemonica.avemusic.music.api.dto.MusicModels.SongCard;
import com.avemonica.avemusic.music.api.service.LyricsService;
import com.avemonica.avemusic.music.api.service.MusicService;
import com.avemonica.minirpc.spring.annotation.MiniRpcReference;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/music/songs")
public class SongController {

    @MiniRpcReference(
            host = "127.0.0.1",
            port = 20882,
            group = "music",
            version = "1.0.0"
    )
    private MusicService musicService;

    @MiniRpcReference(
            host = "127.0.0.1",
            port = 20882,
            group = "music",
            version = "1.0.0",

            /*
             * 歌词翻译需要调用本地大模型，
             * 不能使用普通 RPC 的 3 秒超时。
             */
            timeoutMillis = 100_000
    )
    private LyricsService lyricsService;

    private final ManagementActorResolver actorResolver;
    private final PlaySessionService playSessionService;

    public SongController(
            ManagementActorResolver actorResolver,
            PlaySessionService playSessionService
    ) {
        this.actorResolver = actorResolver;
        this.playSessionService =
                playSessionService;
    }

    @GetMapping("/{songId}/lyrics")
    public ApiResult<LyricsResult>
    lyrics(
            @PathVariable
            String songId
    ) {
        return ApiResult.success(
                lyricsService.getLyrics(
                        songId
                )
        );
    }

    @PostMapping("/{songId}/lyrics/translate")
    public ApiResult<LyricsResult>
    translateLyrics(
            @PathVariable
            String songId
    ) {
        return ApiResult.success(
                lyricsService
                        .translateLyrics(
                                songId
                        )
        );
    }

    @GetMapping("/home")
    public ApiResult<List<SongCard>> homeSongs(
            @RequestParam(defaultValue = "8")
            int limit
    ) {
        return ApiResult.success(
                musicService.listHomeSongs(limit)
        );
    }

    /**
     * 创建服务端播放会话。
     *
     * duration 从 music-provider 查询，
     * 不接受前端传入歌曲时长。
     */
    @PostMapping("/{id}/play-session")
    public ApiResult<PlaySessionService.StartResult>
    startPlaySession(
            @PathVariable
            String id,

            @RequestHeader(
                    name = "X-Playback-Client"
            )
            String playbackClientId,

            HttpServletRequest request
    ) {
        return ApiResult.success(
                playSessionService.start(
                        id,
                        playbackClientId,
                        request.getRemoteAddr(),
                        request.getHeader(
                                "User-Agent"
                        )
                )
        );
    }

    /**
     * 播放器真正处于 playing 状态时发送 heartbeat。
     *
     * 客户端不提交 currentTime，也不提交 playedSeconds；
     * 有效时间由 Redis 中的服务端时间差计算。
     */
    @PostMapping(
            "/play-session/{sessionId}/heartbeat"
    )
    public ApiResult<
            PlaySessionService.HeartbeatResult
            >
    heartbeatPlaySession(
            @PathVariable
            String sessionId,

            @RequestHeader(
                    name = "X-Playback-Client"
            )
            String playbackClientId,

            HttpServletRequest request
    ) {
        return ApiResult.success(
                playSessionService.heartbeat(
                        sessionId,
                        playbackClientId,
                        request.getRemoteAddr(),
                        request.getHeader(
                                "User-Agent"
                        )
                )
        );
    }

    /**
     * 切歌 / 播放结束时 best-effort 清理 Redis session。
     * 即使前端没有成功调用，Redis TTL 也会自动回收。
     */
    @DeleteMapping(
            "/play-session/{sessionId}"
    )
    public ApiResult<Void> finishPlaySession(
            @PathVariable
            String sessionId,

            @RequestHeader(
                    name = "X-Playback-Client"
            )
            String playbackClientId,

            HttpServletRequest request
    ) {
        playSessionService.finish(
                sessionId,
                playbackClientId,
                request.getRemoteAddr(),
                request.getHeader(
                        "User-Agent"
                )
        );

        return ApiResult.success();
    }

    @PreAuthorize("""
        hasAnyAuthority(
            'sys::admin',
            'song::manage'
        )
        """)
    @GetMapping("/manage")
    public ApiResult<
            PageResult<SongItem>
            >
    managedSongs(
            @RequestParam(
                    defaultValue = ""
            )
            String keyword,

            @RequestParam(
                    defaultValue = "1"
            )
            @Min(1)
            int page,

            @RequestParam(
                    defaultValue = "10"
            )
            @Min(1)
            @Max(50)
            int size
    ) {
        return ApiResult.success(
                musicService
                        .pageManagedSongs(
                                keyword,
                                page,
                                size
                        )
        );
    }

    @PreAuthorize("""
            hasAnyAuthority(
                'sys::admin',
                'song::manage'
            )
            """)
    @PostMapping
    public ApiResult<SongItem> createSong(
            @Valid
            @RequestBody
            CreateSongBody body,

            Authentication authentication
    ) {
        return ApiResult.success(
                musicService.createSong(
                        new CreateSongRequest(
                                body.name(),
                                body.albumId(),
                                body.artistIds(),
                                body.durationSeconds(),
                                body.style(),
                                body.introduction(),
                                body.coverUrl(),
                                body.audioUrl(),
                                actorResolver.resolve(
                                        authentication
                                )
                        )
                )
        );
    }

    /**
     * 编辑歌曲。
     *
     * style / coverUrl 不允许客户端直接决定，
     * Provider 会根据 albumId 从专辑重新继承。
     */
    @PreAuthorize("""
            hasAnyAuthority(
                'sys::admin',
                'song::manage'
            )
            """)
    @PutMapping("/{id}")
    public ApiResult<SongItem> updateSong(
            @PathVariable
            String id,

            @Valid
            @RequestBody
            UpdateSongBody body,

            Authentication authentication
    ) {
        return ApiResult.success(
                musicService.updateSong(
                        new UpdateSongRequest(
                                id,
                                body.name(),
                                body.albumId(),
                                body.artistIds(),
                                body.durationSeconds(),
                                body.introduction(),
                                body.audioUrl(),
                                actorResolver.resolve(
                                        authentication
                                )
                        )
                )
        );
    }

    @PreAuthorize("""
            hasAnyAuthority(
                'sys::admin',
                'song::manage'
            )
            """)
    @DeleteMapping("/batch")
    public ApiResult<Void> deleteSongs(
            @Valid
            @RequestBody
            BatchDeleteBody body,

            Authentication authentication
    ) {
        musicService.deleteSongs(
                new BatchDeleteRequest(
                        body.ids(),
                        actorResolver.resolve(
                                authentication
                        )
                )
        );

        return ApiResult.success();
    }

    @PreAuthorize("""
            hasAnyAuthority(
                'sys::admin',
                'sys::audit'
            )
            """)
    @GetMapping("/audit")
    public ApiResult<List<SongItem>>
    auditSongs() {
        return ApiResult.success(
                musicService.listAuditSongs()
        );
    }

    @PreAuthorize("""
            hasAnyAuthority(
                'sys::admin',
                'sys::audit'
            )
            """)
    @PutMapping("/{id}/audit")
    public ApiResult<Void> reviewSong(
            @PathVariable
            String id,

            @Valid
            @RequestBody
            ReviewBody body,

            Authentication authentication
    ) {
        musicService.reviewSong(
                new ReviewRequest(
                        id,
                        body.action(),
                        body.reason(),
                        actorResolver.resolve(
                                authentication
                        )
                )
        );

        return ApiResult.success();
    }

    public record CreateSongBody(
            @NotBlank(message = "音乐名称不能为空")
            @Size(
                    max = 128,
                    message = "音乐名称不能超过128个字符"
            )
            String name,

            String albumId,

            @NotEmpty(
                    message = "至少选择一位音乐人"
            )
            List<String> artistIds,

            @Min(
                    value = 1,
                    message = "音乐时长必须大于0"
            )
            @Max(
                    value = 86_400,
                    message = "音乐时长不能超过86400秒"
            )
            int durationSeconds,

            @Size(
                    max = 64,
                    message = "音乐风格不能超过64个字符"
            )
            String style,

            @Size(
                    max = 1000,
                    message = "音乐简介不能超过1000个字符"
            )
            String introduction,

            @Size(
                    max = 512,
                    message = "封面URL过长"
            )
            String coverUrl,

            @NotBlank(
                    message = "音乐文件地址不能为空"
            )
            @Size(
                    max = 512,
                    message = "音乐文件URL过长"
            )
            String audioUrl
    ) {
    }

    public record UpdateSongBody(
            @NotBlank(message = "音乐名称不能为空")
            @Size(
                    max = 128,
                    message = "音乐名称不能超过128个字符"
            )
            String name,

            String albumId,

            @NotEmpty(
                    message = "至少选择一位音乐人"
            )
            List<String> artistIds,

            @Min(
                    value = 1,
                    message = "音乐时长必须大于0"
            )
            @Max(
                    value = 86_400,
                    message = "音乐时长不能超过86400秒"
            )
            int durationSeconds,

            @Size(
                    max = 1000,
                    message = "音乐简介不能超过1000个字符"
            )
            String introduction,

            @NotBlank(
                    message = "音乐文件地址不能为空"
            )
            @Size(
                    max = 512,
                    message = "音乐文件URL过长"
            )
            String audioUrl
    ) {
    }

    public record BatchDeleteBody(
            @NotEmpty(
                    message = "至少选择一条需要删除的音乐"
            )
            @Size(
                    max = 100,
                    message = "单次最多删除100条音乐"
            )
            List<String> ids
    ) {
    }

    public record ReviewBody(
            @NotBlank(
                    message = "审核动作不能为空"
            )
            @Pattern(
                    regexp = "APPROVE|REJECT|REVOKE",
                    message = "审核动作不正确"
            )
            String action,

            @Size(
                    max = 255,
                    message = "驳回原因不能超过255个字符"
            )
            String reason
    ) {
    }
}
