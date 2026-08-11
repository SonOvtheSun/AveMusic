package com.avemonica.avemusic.gateway.controller.music;

import com.avemonica.avemusic.common.web.ApiResult;
import com.avemonica.avemusic.music.api.dto.ArtistPublicModels.ArtistDetail;
import com.avemonica.avemusic.gateway.security.ManagementActorResolver;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.ArtistItem;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.ArtistSearchItem;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.ArtistStatusRequest;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.ArtistDeleteRequest;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.BatchDeleteRequest;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.CreateArtistRequest;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.UpdateArtistRequest;
import com.avemonica.avemusic.music.api.dto.MusicManagementModels.ReviewRequest;
import com.avemonica.avemusic.music.api.dto.MusicModels.ArtistCard;
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
@RequestMapping("/api/music/artists")
public class ArtistController {

    @MiniRpcReference(
            host = "127.0.0.1",
            port = 20882,
            group = "music",
            version = "1.0.0"
    )
    private MusicService musicService;

    private final ManagementActorResolver actorResolver;

    public ArtistController(
            ManagementActorResolver actorResolver
    ) {
        this.actorResolver = actorResolver;
    }

    @GetMapping("/home")
    public ApiResult<List<ArtistCard>> homeArtists(
            @RequestParam(defaultValue = "6")
            int limit
    ) {
        return ApiResult.success(
                musicService.listHomeArtists(
                        limit
                )
        );
    }

    /**
     * C端公开音乐人详情。
     * 路径单独放在 /detail 下，避免把 /manage、/audit、/search
     * 一并加入 Security permitAll。
     */
    @GetMapping("/detail/{id}")
    public ApiResult<ArtistDetail> artistDetail(
            @PathVariable
            String id
    ) {
        return ApiResult.success(
                musicService.getArtistDetail(id)
        );
    }

    @PreAuthorize("""
            hasAnyAuthority(
                'sys::admin',
                'artist::manage'
            )
            """)
    @GetMapping("/manage")
    public ApiResult<List<ArtistItem>>
    managedArtists() {
        return ApiResult.success(
                musicService.listManagedArtists()
        );
    }

    /**
     * 新增歌曲表单使用。
     * 搜索结果同时包含 APPROVED / PENDING 音乐人。
     */
    @PreAuthorize("""
            hasAnyAuthority(
                'sys::admin',
                'song::manage',
                'song::add'
            )
            """)
    @GetMapping("/search")
    public ApiResult<List<ArtistSearchItem>> searchArtists(
            @RequestParam
            @NotBlank(message = "搜索关键词不能为空")
            String keyword,

            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "limit不能小于1")
            @Max(value = 20, message = "limit不能大于20")
            int limit
    ) {
        return ApiResult.success(
                musicService.searchArtists(
                        keyword,
                        limit
                )
        );
    }

    /**
     * 管理人员新增音乐人。
     * SUPER_ADMIN 直接通过，OPERATOR 进入 PENDING。
     */
    @PreAuthorize("""
            hasAnyAuthority(
                'sys::admin',
                'artist::manage'
            )
            """)
    @PostMapping
    public ApiResult<ArtistSearchItem> createArtist(
            @Valid
            @RequestBody
            CreateArtistBody body,

            Authentication authentication
    ) {
        return ApiResult.success(
                musicService.createArtist(
                        new CreateArtistRequest(
                                body.name(),
                                body.translatedName(),
                                body.countryRegion(),
                                body.style(),
                                body.introduction(),
                                body.avatarUrl(),
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
                'artist::manage'
            )
            """)
    @PutMapping("/{id}")
    public ApiResult<ArtistItem> updateArtist(
            @PathVariable String id,
            @Valid @RequestBody CreateArtistBody body,
            Authentication authentication
    ) {
        return ApiResult.success(
                musicService.updateArtist(
                        new UpdateArtistRequest(
                                id,
                                body.name(),
                                body.translatedName(),
                                body.countryRegion(),
                                body.style(),
                                body.introduction(),
                                body.avatarUrl(),
                                actorResolver.resolve(authentication)
                        )
                )
        );
    }

    @PreAuthorize("""
            hasAnyAuthority(
                'sys::admin',
                'artist::manage'
            )
            """)
    @PutMapping("/{id}/status")
    public ApiResult<Void> setArtistStatus(
            @PathVariable String id,
            @RequestBody ArtistStatusBody body,
            Authentication authentication
    ) {
        musicService.setArtistOnline(
                new ArtistStatusRequest(
                        id,
                        body.online(),
                        actorResolver.resolve(authentication)
                )
        );
        return ApiResult.success();
    }

    @PreAuthorize("""
            hasAnyAuthority(
                'sys::admin',
                'artist::manage'
            )
            """)
    @DeleteMapping("/{id}")
    public ApiResult<Void> deleteArtist(
            @PathVariable String id,
            Authentication authentication
    ) {
        musicService.deleteArtist(
                new ArtistDeleteRequest(
                        id,
                        actorResolver.resolve(authentication)
                )
        );
        return ApiResult.success();
    }

    @PreAuthorize("""
            hasAnyAuthority(
                'sys::admin',
                'artist::manage'
            )
            """)
    @DeleteMapping("/batch")
    public ApiResult<Void> deleteArtists(
            @Valid
            @RequestBody
            BatchDeleteBody body,

            Authentication authentication
    ) {
        musicService.deleteArtists(
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
    public ApiResult<List<ArtistItem>>
    auditArtists() {
        return ApiResult.success(
                musicService.listAuditArtists()
        );
    }

    @PreAuthorize("""
            hasAnyAuthority(
                'sys::admin',
                'sys::audit'
            )
            """)
    @PutMapping("/{id}/audit")
    public ApiResult<Void> reviewArtist(
            @PathVariable
            String id,

            @Valid
            @RequestBody
            ReviewBody body,

            Authentication authentication
    ) {
        musicService.reviewArtist(
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

    public record CreateArtistBody(
            @NotBlank(message = "音乐人名称不能为空")
            @Size(max = 128, message = "音乐人名称不能超过128个字符")
            String name,

            @Size(max = 128, message = "译名不能超过128个字符")
            String translatedName,

            @NotBlank(message = "国家或地区不能为空")
            @Size(max = 64, message = "国家或地区不能超过64个字符")
            String countryRegion,

            @Size(max = 64, message = "音乐风格不能超过64个字符")
            String style,

            @Size(max = 1000, message = "音乐人简介不能超过1000个字符")
            String introduction,

            @Size(max = 512, message = "头像URL过长")
            String avatarUrl
    ) {
    }

    public record BatchDeleteBody(
            @NotEmpty(
                    message = "至少选择一位需要删除的音乐人"
            )
            @Size(
                    max = 100,
                    message = "单次最多删除100位音乐人"
            )
            List<String> ids
    ) {
    }

    public record ArtistStatusBody(
            boolean online
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
