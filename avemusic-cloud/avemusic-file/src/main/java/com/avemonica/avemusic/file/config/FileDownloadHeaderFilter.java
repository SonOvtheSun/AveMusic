package com.avemonica.avemusic.file.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 公开文件默认可直接浏览 / 播放。
 *
 * 当请求：
 *
 * /files/.../song.flac?download=1
 *
 * 时增加 Content-Disposition: attachment，
 * 让浏览器执行下载而不是打开内置音频播放器。
 */
@Component
public final class FileDownloadHeaderFilter
        extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(
            HttpServletRequest request
    ) {
        String uri =
                request.getRequestURI();

        return !uri.startsWith(
                "/files/"
        )
                || !"1".equals(
                request.getParameter(
                        "download"
                )
        );
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        response.setHeader(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment"
        );

        filterChain.doFilter(
                request,
                response
        );
    }
}
