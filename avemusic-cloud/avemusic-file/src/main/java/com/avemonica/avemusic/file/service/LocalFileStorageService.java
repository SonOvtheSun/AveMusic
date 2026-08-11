package com.avemonica.avemusic.file.service;

import com.avemonica.avemusic.file.config.FileStorageProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public final class LocalFileStorageService {

    private static final DateTimeFormatter
            DATE_DIRECTORY_FORMAT =
            DateTimeFormatter.ofPattern(
                    "yyyy/MM/dd/HH"
            );

    private final FileStorageProperties properties;

    public LocalFileStorageService(
            FileStorageProperties properties
    ) {
        this.properties = properties;
    }

    @PostConstruct
    void initialize() throws IOException {
        Files.createDirectories(
                properties.root()
        );
    }

    public StoredFile store(
            MultipartFile file,
            StorageCategory category
    ) throws IOException {
        validate(file, category);

        String extension =
                extractExtension(
                        file.getOriginalFilename()
                );

        String dateDirectory =
                LocalDateTime.now().format(
                        DATE_DIRECTORY_FORMAT
                );

        Path relativeDirectory =
                Path.of(
                        category.directory(),
                        dateDirectory
                );

        Path targetDirectory =
                properties.root()
                        .resolve(relativeDirectory)
                        .normalize();

        ensureInsideRoot(targetDirectory);
        Files.createDirectories(targetDirectory);

        String fileName =
                UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        + "."
                        + extension;

        Path target =
                targetDirectory
                        .resolve(fileName)
                        .normalize();

        ensureInsideRoot(target);

        try (InputStream inputStream =
                     file.getInputStream()) {
            Files.copy(
                    inputStream,
                    target
            );
        }

        String relativePath =
                properties.root()
                        .relativize(target)
                        .toString()
                        .replace('\\', '/');

        String url =
                properties.publicBaseUrl()
                        + "/files/"
                        + relativePath;

        return new StoredFile(
                category.requestValue(),
                file.getOriginalFilename(),
                fileName,
                relativePath,
                url,
                file.getSize(),
                contentType(file)
        );
    }

    private static void validate(
            MultipartFile file,
            StorageCategory category
    ) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "上传文件不能为空"
            );
        }

        if (file.getSize() > category.maxBytes()) {
            throw new IllegalArgumentException(
                    category.displayName()
                            + "不能超过"
                            + category.maxDisplaySize()
            );
        }

        String extension =
                extractExtension(
                        file.getOriginalFilename()
                );

        if (!category.extensions()
                .contains(extension)) {
            throw new IllegalArgumentException(
                    category.displayName()
                            + "不支持该文件格式，可用格式："
                            + String.join(
                            ", ",
                            category.extensions()
                    )
            );
        }

        if (category.image()) {
            validateImage(file);
        } else {
            validateAudioContentType(file);
        }
    }

    private static void validateImage(
            MultipartFile file
    ) throws IOException {
        try (InputStream inputStream =
                     file.getInputStream()) {
            if (ImageIO.read(inputStream) == null) {
                throw new IllegalArgumentException(
                        "文件内容不是有效图片"
                );
            }
        }
    }

    private static void validateAudioContentType(
            MultipartFile file
    ) {
        String contentType =
                file.getContentType();

        if (contentType == null
                || contentType.isBlank()
                || "application/octet-stream"
                .equalsIgnoreCase(contentType)) {
            return;
        }

        if (!contentType.startsWith("audio/")
                && !"application/ogg"
                .equalsIgnoreCase(contentType)) {
            throw new IllegalArgumentException(
                    "文件内容类型不是音频"
            );
        }
    }

    private static String extractExtension(
            String originalFileName
    ) {
        String cleanedName =
                StringUtils.cleanPath(
                        originalFileName == null
                                ? ""
                                : originalFileName
                );

        int dotIndex =
                cleanedName.lastIndexOf('.');

        if (dotIndex < 0
                || dotIndex
                == cleanedName.length() - 1) {
            throw new IllegalArgumentException(
                    "文件缺少扩展名"
            );
        }

        return cleanedName
                .substring(dotIndex + 1)
                .toLowerCase(Locale.ROOT);
    }

    private void ensureInsideRoot(Path path) {
        if (!path.startsWith(
                properties.root()
        )) {
            throw new IllegalArgumentException(
                    "非法文件路径"
            );
        }
    }

    private static String contentType(
            MultipartFile file
    ) {
        String contentType =
                file.getContentType();

        return contentType == null
                || contentType.isBlank()
                ? "application/octet-stream"
                : contentType;
    }

    public enum StorageCategory {

        AVATAR(
                "avatar",
                "avatar",
                "头像",
                10L * 1024 * 1024,
                "10MB",
                true,
                Set.of(
                        "jpg",
                        "jpeg",
                        "png"
                )
        ),

        ALBUM_COVER(
                "album-cover",
                "album-cover",
                "专辑封面",
                10L * 1024 * 1024,
                "10MB",
                true,
                Set.of(
                        "jpg",
                        "jpeg",
                        "png"
                )
        ),

        AUDIO(
                "audio",
                "audio",
                "音频文件",
                300L * 1024 * 1024,
                "300MB",
                false,
                Set.of(
                        "mp3",
                        "wav",
                        "flac",
                        "m4a",
                        "ogg"
                )
        );

        private final String requestValue;
        private final String directory;
        private final String displayName;
        private final long maxBytes;
        private final String maxDisplaySize;
        private final boolean image;
        private final Set<String> extensions;

        StorageCategory(
                String requestValue,
                String directory,
                String displayName,
                long maxBytes,
                String maxDisplaySize,
                boolean image,
                Set<String> extensions
        ) {
            this.requestValue = requestValue;
            this.directory = directory;
            this.displayName = displayName;
            this.maxBytes = maxBytes;
            this.maxDisplaySize = maxDisplaySize;
            this.image = image;
            this.extensions = extensions;
        }

        public static StorageCategory from(
                String value
        ) {
            for (StorageCategory category :
                    values()) {
                if (category.requestValue
                        .equalsIgnoreCase(value)) {
                    return category;
                }
            }

            throw new IllegalArgumentException(
                    "文件分类只能是："
                            + "avatar、album-cover、audio"
            );
        }

        public String requestValue() {
            return requestValue;
        }

        public String directory() {
            return directory;
        }

        public String displayName() {
            return displayName;
        }

        public long maxBytes() {
            return maxBytes;
        }

        public String maxDisplaySize() {
            return maxDisplaySize;
        }

        public boolean image() {
            return image;
        }

        public Set<String> extensions() {
            return extensions;
        }
    }

    public record StoredFile(
            String category,
            String originalName,
            String fileName,
            String relativePath,
            String url,
            long size,
            String contentType
    ) {
    }
}
