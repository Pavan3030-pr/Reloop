package app.reloop.service;

import app.reloop.config.AppProperties;
import app.reloop.exception.BadRequestException;
import app.reloop.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Stores uploaded images. Only the "local" provider is implemented in this build;
 * production deployments should swap in an S3-compatible provider (e.g. Supabase Storage)
 * behind the same interface-style API.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageStorageService {

    private static final long MAX_BYTES = 8L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    private final AppProperties properties;

    public record StoredImage(String key, String publicUrl) {}

    /**
     * Validates and stores an uploaded image, returning its storage key and public URL.
     * Validation covers extension, declared MIME type, size, and magic bytes.
     */
    public StoredImage store(MultipartFile file, String subdir) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("No image file was provided");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BadRequestException("Image exceeds the maximum size of 8MB");
        }

        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String extension = extensionOf(original);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BadRequestException("Only JPG, PNG, or WEBP images are supported");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BadRequestException("Unsupported image content type");
        }

        try (InputStream in = file.getInputStream()) {
            String sniffed = sniffMimeType(in);
            if (sniffed == null || !ALLOWED_CONTENT_TYPES.contains(sniffed)) {
                throw new BadRequestException("File content does not look like a valid image");
            }
            if (!sniffed.equals(contentType) && !(sniffed.equals("image/jpeg") && contentType.equals("image/jpg"))) {
                throw new BadRequestException("Declared content type does not match file content");
            }
        } catch (IOException e) {
            throw new BadRequestException("Could not read the uploaded file");
        }

        String key = "%s/%s/%s.%s".formatted(
                subdir,
                LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM")),
                UUID.randomUUID(),
                extension.equals("jpeg") ? "jpg" : extension);

        String type = properties.storage() != null ? properties.storage().type() : "local";
        if (!"local".equals(type)) {
            throw new ServiceUnavailableException(
                    "Configured storage provider '" + type + "' is not available in this build; use STORAGE_TYPE=local");
        }

        try {
            Path baseDir = Paths.get(properties.storage().localDir()).toAbsolutePath().normalize();
            Path target = baseDir.resolve(key).normalize();
            if (!target.startsWith(baseDir)) {
                throw new BadRequestException("Invalid storage path");
            }
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            String publicBase = properties.storage().publicBaseUrl();
            String base = publicBase == null ? "/uploads" : publicBase.replaceAll("/+$", "");
            String url = base + "/" + key;
            log.info("Stored image under key {}", key);
            return new StoredImage(key, url);
        } catch (IOException e) {
            log.error("Failed to store uploaded image", e);
            throw new ServiceUnavailableException("Could not store the uploaded image. Please try again.");
        }
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase();
    }

    /** Reads the first bytes and identifies JPEG/PNG/WEBP by magic number. */
    private String sniffMimeType(InputStream in) throws IOException {
        byte[] header = new byte[12];
        int read = in.readNBytes(header, 0, header.length);
        if (read < 4) {
            return null;
        }
        if ((header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if ((header[0] & 0xFF) == 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47) {
            return "image/png";
        }
        if (header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && read >= 12 && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    /** Placeholder for future provider metadata (kept explicit to avoid silent behavior). */
    public Map<String, String> describeProvider() {
        String type = properties.storage() != null ? properties.storage().type() : "local";
        return Map.of("provider", "local".equals(type) ? "local-disk" : type);
    }
}
