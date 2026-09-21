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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Validates and stores uploaded images. Only the "local" provider is implemented in this build;
 * production deployments should swap in an S3-compatible provider (e.g. Supabase Storage)
 * behind the same interface-style API.
 *
 * <p>Validation is <strong>content-authoritative</strong>: the bytes decide whether an upload is a
 * supported image, and the stored extension is derived from those bytes. The filename and the
 * client-declared content type are advisory only — they are frequently wrong or absent in the real
 * world (mobile galleries and clipboard paste often send no extension, and some sources declare
 * {@code application/octet-stream}), and rejecting on them turns away perfectly good photos.
 * Non-images are still refused, because magic-byte sniffing is the gate.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageStorageService {

    private static final long MAX_BYTES = 8L * 1024 * 1024;

    /** Canonical PNG signature: 89 50 4E 47 0D 0A 1A 0A */
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    /** JPEG start-of-image marker plus a following marker byte: FF D8 FF. */
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    /** The PNG spec requires IHDR to be the first chunk, so this confirms real structure. */
    private static final byte[] PNG_FIRST_CHUNK = {'I', 'H', 'D', 'R'};
    /** IHDR follows the 8-byte signature and the 4-byte chunk length: 8 + 4 = 12. */
    private static final int PNG_FIRST_CHUNK_OFFSET = PNG_SIGNATURE.length + 4;

    /** Bytes needed to identify the longest signature we check (PNG signature + IHDR). */
    private static final int SNIFF_BYTES = 16;

    /** Extension assigned per detected type — derived from content, never from the client. */
    private static final Map<String, String> EXTENSION_BY_TYPE = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp");

    /** Only used to note conventional metadata in a debug line; it never decides an upload. */
    private static final Set<String> CONVENTIONAL_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    private final AppProperties properties;

    public record StoredImage(String key, String publicUrl) {}

    /**
     * Checks that an upload really is a supported image by inspecting its content, and returns the
     * detected MIME type. Used before expensive work (such as calling Gemini) where the
     * client-declared filename and content type are only advisory.
     */
    public String detectImageType(MultipartFile file) {
        requireUsableUpload(file);
        String detected;
        try (InputStream in = file.getInputStream()) {
            detected = sniffMimeType(in);
        } catch (IOException e) {
            throw new BadRequestException("Could not read the uploaded file");
        }
        if (detected == null) {
            throw unsupportedImage();
        }
        return detected;
    }

    /**
     * Validates and stores an uploaded image, returning its storage key and public URL.
     * Size, content, and path safety are enforced; the stored extension comes from the detected
     * content, so a client cannot influence how the file is served.
     */
    public StoredImage store(MultipartFile file, String subdir) {
        requireUsableUpload(file);
        String detected = detectImageType(file);
        String extension = EXTENSION_BY_TYPE.get(detected);

        String declaredName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String declaredType = (file.getContentType() == null ? "" : file.getContentType()).toLowerCase(Locale.ROOT);
        if (!declaredType.equals(detected) || !CONVENTIONAL_EXTENSIONS.contains(extensionOf(declaredName))) {
            log.debug("Upload metadata disagrees with content: declared name='{}', declared type='{}', detected='{}'",
                    declaredName, declaredType, detected);
        }

        String key = "%s/%s/%s.%s".formatted(
                subdir,
                LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM")),
                UUID.randomUUID(),
                extension);

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

    private void requireUsableUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("No image file was provided");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BadRequestException("Image exceeds the maximum size of 8MB");
        }
    }

    private BadRequestException unsupportedImage() {
        return new BadRequestException(
                "This file is not a JPG, PNG, or WEBP image. Upload a photo of the item instead.");
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * Identifies JPEG/PNG/WEBP by their leading bytes, returning {@code null} when the content is
     * none of them.
     *
     * <ul>
     *   <li>PNG — {@code 89 50 4E 47 0D 0A 1A 0A}, then the mandatory first {@code IHDR} chunk at offset 12</li>
     *   <li>JPEG — {@code FF D8 FF}</li>
     *   <li>WEBP — {@code RIFF} at offset 0 and {@code WEBP} at offset 8</li>
     * </ul>
     */
    private String sniffMimeType(InputStream in) throws IOException {
        byte[] header = in.readNBytes(SNIFF_BYTES);
        if (startsWith(header, 0, PNG_SIGNATURE) && startsWith(header, PNG_FIRST_CHUNK_OFFSET, PNG_FIRST_CHUNK)) {
            return "image/png";
        }
        if (startsWith(header, 0, JPEG_SIGNATURE)) {
            return "image/jpeg";
        }
        if (startsWith(header, 0, "RIFF") && startsWith(header, 8, "WEBP")) {
            return "image/webp";
        }
        return null;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        return startsWith(data, 0, prefix);
    }

    private static boolean startsWith(byte[] data, int offset, byte[] prefix) {
        if (data.length < offset + prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[offset + i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean startsWith(byte[] data, int offset, String ascii) {
        if (data.length < offset + ascii.length()) {
            return false;
        }
        for (int i = 0; i < ascii.length(); i++) {
            if ((data[offset + i] & 0xFF) != ascii.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /** Placeholder for future provider metadata (kept explicit to avoid silent behavior). */
    public Map<String, String> describeProvider() {
        String type = properties.storage() != null ? properties.storage().type() : "local";
        return Map.of("provider", "local".equals(type) ? "local-disk" : type);
    }
}
