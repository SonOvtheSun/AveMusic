package com.avemonica.avemusic.gateway.controller.music;

import com.avemonica.avemusic.common.web.ApiResult;
import com.avemonica.avemusic.music.api.dto.MusicModels.SearchResult;
import com.avemonica.avemusic.music.api.service.MusicService;
import com.avemonica.minirpc.spring.annotation.MiniRpcReference;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/music/search")
public final class SearchController {

    @MiniRpcReference(
            host = "127.0.0.1",
            port = 20882,
            group = "music",
            version = "1.0.0",
            timeoutMillis = 15_000
    )
    private MusicService musicService;

    @GetMapping
    public ApiResult<SearchResult> search(
            @RequestParam
            @Size(
                    min = 1,
                    max = 64
            )
            String keyword,

            @RequestParam(
                    defaultValue = "5"
            )
            @Min(1)
            @Max(8)
            int limit
    ) {
        return ApiResult.success(
                musicService.search(
                        keyword,
                        limit
                )
        );
    }
}
