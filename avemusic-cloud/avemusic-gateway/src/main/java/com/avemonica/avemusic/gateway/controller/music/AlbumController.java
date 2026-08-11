package com.avemonica.avemusic.gateway.controller.music;

import com.avemonica.avemusic.common.web.ApiResult;
import com.avemonica.avemusic.music.api.dto.AlbumPublicModels.AlbumDetail;
import com.avemonica.avemusic.gateway.security.ManagementActorResolver;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.AlbumCreateResult;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.AlbumItem;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.AlbumSearchItem;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.BatchDeleteRequest;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.CreateAlbumSongRequest;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.CreateAlbumWithSongsRequest;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.ReviewRequest;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.UpdateAlbumRequest;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.PageResult;
import com.avemonica.avemusic.music.api.service.MusicService;
import com.avemonica.minirpc.spring.annotation.MiniRpcReference;
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
@RequestMapping("/api/music/albums")
public class AlbumController {

    @MiniRpcReference(
            host = "127.0.0.1",
            port = 20882,
            group = "music",
            version = "1.0.0"
    )
    private MusicService musicService;

    private final ManagementActorResolver actorResolver;

    public AlbumController(
            ManagementActorResolver actorResolver
    ) {
        this.actorResolver = actorResolver;
    }

    /**
     * C端公开专辑详情。
     *
     * 不使用 @PreAuthorize，
     * SecurityConfig 中单独 permitAll。
     */
    @GetMapping("/detail/{id}")
    public ApiResult<AlbumDetail>
    albumDetail(
            @PathVariable
            String id
    ) {
        return ApiResult.success(
                musicService
                        .getAlbumDetail(id)
        );
    }

    @PreAuthorize("""
        hasAnyAuthority(
            'sys::admin',
            'song::manage'
        )
        """)
    @GetMapping("/manage")
    public ApiResult<
            PageResult<AlbumItem>
            >
    managedAlbums(
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
                        .pageManagedAlbums(
                                keyword,
                                page,
                                size
                        )
        );
    }

    @PreAuthorize("""
            hasAnyAuthority(
                'sys::admin',
                'song::manage',
                'song::add'
            )
            """)
    @GetMapping("/search")
    public ApiResult<List<AlbumSearchItem>>
    searchAlbums(
            @RequestParam
            @NotBlank(
                    message = "搜索关键词不能为空"
            )
            String keyword,

            @RequestParam(defaultValue = "10")
            @Min(value = 1)
            @Max(value = 20)
            int limit
    ) {
        return ApiResult.success(
                musicService.searchAlbums(
                        keyword,
                        limit
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
    public ApiResult<AlbumCreateResult>
    createAlbum(
            @Valid
            @RequestBody
            CreateAlbumBody body,

            Authentication authentication
    ) {
        List<CreateAlbumSongRequest> songs =
                body.songs()
                        .stream()
                        .map(song ->
                                new CreateAlbumSongRequest(
                                        song.name(),
                                        song.artistIds(),
                                        song.durationSeconds(),
                                        song.introduction(),
                                        song.audioUrl()
                                )
                        )
                        .toList();

        return ApiResult.success(
                musicService.createAlbumWithSongs(
                        new CreateAlbumWithSongsRequest(
                                body.name(),
                                body.artistIds(),
                                body.style(),
                                body.coverUrl(),
                                body.releaseDate(),
                                body.introduction(),
                                songs,
                                actorResolver.resolve(
                                        authentication
                                )
                        )
                )
        );
    }

    /**
     * 编辑专辑本身，不直接在该接口中编辑歌曲独立字段。
     *
     * style / cover 修改后 Provider 会同步到专辑歌曲。
     */
    @PreAuthorize("""
            hasAnyAuthority(
                'sys::admin',
                'song::manage'
            )
            """)
    @PutMapping("/{id}")
    public ApiResult<AlbumItem> updateAlbum(
            @PathVariable
            String id,

            @Valid
            @RequestBody
            UpdateAlbumBody body,

            Authentication authentication
    ) {
        return ApiResult.success(
                musicService.updateAlbum(
                        new UpdateAlbumRequest(
                                id,
                                body.name(),
                                body.artistIds(),
                                body.style(),
                                body.coverUrl(),
                                body.releaseDate(),
                                body.introduction(),
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
    public ApiResult<Void> deleteAlbums(
            @Valid
            @RequestBody
            BatchDeleteBody body,

            Authentication authentication
    ) {
        musicService.deleteAlbums(
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
    public ApiResult<List<AlbumItem>>
    auditAlbums() {
        return ApiResult.success(
                musicService.listAuditAlbums()
        );
    }

    @PreAuthorize("""
            hasAnyAuthority(
                'sys::admin',
                'sys::audit'
            )
            """)
    @PutMapping("/{id}/audit")
    public ApiResult<Void> reviewAlbum(
            @PathVariable
            String id,

            @Valid
            @RequestBody
            ReviewBody body,

            Authentication authentication
    ) {
        musicService.reviewAlbum(
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

    public record CreateAlbumBody(
            @NotBlank(message = "专辑名称不能为空")
            @Size(max = 128)
            String name,

            @NotEmpty(
                    message = "专辑至少选择一位音乐人"
            )
            List<String> artistIds,

            @NotBlank(
                    message = "专辑音乐风格不能为空"
            )
            @Size(max = 64)
            String style,

            @NotBlank(
                    message = "专辑封面不能为空"
            )
            @Size(max = 512)
            String coverUrl,

            @Pattern(
                    regexp = "^$|\\d{4}-\\d{2}-\\d{2}$",
                    message = "发行日期格式必须为yyyy-MM-dd"
            )
            String releaseDate,

            @Size(max = 1000)
            String introduction,

            @NotEmpty(
                    message = "专辑至少需要包含一首音乐"
            )
            @Size(
                    max = 50,
                    message = "单张专辑最多一次新增50首音乐"
            )
            List<@Valid CreateAlbumSongBody> songs
    ) {
    }

    public record CreateAlbumSongBody(
            @NotBlank(message = "音乐名称不能为空")
            @Size(max = 128)
            String name,

            @NotEmpty(
                    message = "每首音乐至少选择一位音乐人"
            )
            List<String> artistIds,

            @Min(value = 1)
            @Max(value = 86_400)
            int durationSeconds,

            @Size(max = 1000)
            String introduction,

            @NotBlank(
                    message = "音乐文件地址不能为空"
            )
            @Size(max = 512)
            String audioUrl
    ) {
    }

    public record UpdateAlbumBody(
            @NotBlank(message = "专辑名称不能为空")
            @Size(max = 128)
            String name,

            @NotEmpty(
                    message = "专辑至少选择一位音乐人"
            )
            List<String> artistIds,

            @NotBlank(
                    message = "专辑音乐风格不能为空"
            )
            @Size(max = 64)
            String style,

            @NotBlank(
                    message = "专辑封面不能为空"
            )
            @Size(max = 512)
            String coverUrl,

            @Pattern(
                    regexp = "^$|\\d{4}-\\d{2}-\\d{2}$",
                    message = "发行日期格式必须为yyyy-MM-dd"
            )
            String releaseDate,

            @Size(max = 1000)
            String introduction
    ) {
    }

    public record BatchDeleteBody(
            @NotEmpty(
                    message = "至少选择一条需要删除的专辑"
            )
            @Size(
                    max = 100,
                    message = "单次最多删除100条专辑"
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

            @Size(max = 255)
            String reason
    ) {
    }
}
