package app.reloop.service;

import app.reloop.config.AppProperties;
import app.reloop.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Content-based image validation. The regression these tests lock in: a genuine PNG used to be
 * rejected with "Only JPG, PNG, or WEBP images are supported" when its filename had no extension or
 * the browser declared a content type outside the allow-list — even though the bytes were a perfectly
 * valid PNG. The bytes now decide; metadata is advisory.
 */
class ImageStorageServiceTest {

    /** A real, decodable 1x1 PNG (PNG signature + IHDR + IDAT + IEND). */
    private static final byte[] REAL_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJ"
                    + "AAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    /** JPEG SOI + APP0/JFIF header + end-of-image: starts FF D8 FF. */
    private static final byte[] REAL_JPEG = concat(
            hex("ffd8ffe000104a46494600010100000100010000ffdb004300"),
            new byte[64],
            hex("ffd9"));

    /** RIFF....WEBP container holding a VP8L chunk. */
    private static final byte[] REAL_WEBP = concat(
            "RIFF".getBytes(StandardCharsets.US_ASCII),
            // chunk size (12) is not validated by the sniffer, but keep the file structurally sane
            new byte[]{0x24, 0x00, 0x00, 0x00},
            "WEBP".getBytes(StandardCharsets.US_ASCII),
            "VP8L".getBytes(StandardCharsets.US_ASCII),
            new byte[]{0x20, 0x00, 0x00, 0x00},
            new byte[32]);

    @TempDir
    Path uploadDir;

    private ImageStorageService service;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties(
                null,
                null,
                null,
                new AppProperties.Storage("local", uploadDir.toString(), "/uploads"),
                null,
                false);
        service = new ImageStorageService(properties);
    }

    private static MultipartFile upload(String filename, String contentType, byte[] bytes) {
        return new MockMultipartFile("image", filename, contentType, bytes);
    }

    @Test
    @DisplayName("real PNG, JPEG and WEBP bytes are recognised by content")
    void recognisesSupportedFormatsByContent() {
        assertThat(service.detectImageType(upload("a.png", "image/png", REAL_PNG))).isEqualTo("image/png");
        assertThat(service.detectImageType(upload("a.jpg", "image/jpeg", REAL_JPEG))).isEqualTo("image/jpeg");
        assertThat(service.detectImageType(upload("a.webp", "image/webp", REAL_WEBP))).isEqualTo("image/webp");
    }

    @Test
    @DisplayName("a real PNG is accepted when the browser sends no filename extension and no content type")
    void acceptsPngWithoutExtensionOrDeclaredType() {
        // Exactly what is reported by mobile galleries and clipboard paste: name "photo", type null.
        MultipartFile file = upload("photo", null, REAL_PNG);

        assertThat(service.detectImageType(file)).isEqualTo("image/png");

        ImageStorageService.StoredImage stored = service.store(file, "scans");
        assertThat(stored.key()).endsWith(".png");
        assertThat(stored.publicUrl()).isEqualTo("/uploads/" + stored.key());
    }

    @Test
    @DisplayName("a real PNG is accepted when the browser declares application/octet-stream")
    void acceptsPngWithOctetStreamDeclaredType() {
        MultipartFile file = upload("photo.png", "application/octet-stream", REAL_PNG);

        assertThat(service.detectImageType(file)).isEqualTo("image/png");
        assertThat(service.store(file, "scans").key()).endsWith(".png");
    }

    @Test
    @DisplayName("the stored extension comes from the content, never from the client filename")
    void storedExtensionIsDerivedFromContent() {
        // A client that names a PNG as .php must not get a .php file written to the upload directory.
        ImageStorageService.StoredImage stored = service.store(upload("payload.php", "text/x-php", REAL_PNG), "scans");

        assertThat(stored.key()).endsWith(".png").doesNotEndWith(".php");
        assertThat(uploadDir.resolve(stored.key())).exists();
    }

    @Test
    @DisplayName("a JPEG named without an extension is also accepted")
    void acceptsJpegWithoutExtension() {
        assertThat(service.store(upload("IMG_2026", null, REAL_JPEG), "scans").key()).endsWith(".jpg");
    }

    @Test
    @DisplayName("non-image bytes are rejected even when named and declared as a PNG")
    void rejectsNonImageContent() {
        byte[] text = "this is plainly not an image".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service.detectImageType(upload("payload.png", "image/png", text)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("JPG, PNG, or WEBP");

        assertThatThrownBy(() -> service.store(upload("payload.png", "image/png", text), "scans"))
                .isInstanceOf(BadRequestException.class);
        assertThat(Files.exists(uploadDir.resolve("scans"))).isFalse();
    }

    @Test
    @DisplayName("the PNG signature alone is not enough — the mandatory IHDR chunk is required")
    void rejectsBytesThatOnlyFakeThePngSignature() {
        byte[] signatureOnly = concat(hex("89504e470d0a1a0a"), "not really a png file".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.detectImageType(upload("fake.png", "image/png", signatureOnly)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("JPG, PNG, or WEBP");
    }

    @Test
    @DisplayName("an empty upload is rejected")
    void rejectsEmptyUpload() {
        assertThatThrownBy(() -> service.detectImageType(upload("empty.png", "image/png", new byte[0])))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("No image file");
        assertThatThrownBy(() -> service.detectImageType(null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("No image file");
    }

    @Test
    @DisplayName("an upload larger than 8MB is rejected")
    void rejectsOversizedUpload() {
        byte[] tooBig = new byte[(8 * 1024 * 1024) + 1];
        System.arraycopy(REAL_PNG, 0, tooBig, 0, REAL_PNG.length);

        assertThatThrownBy(() -> service.detectImageType(upload("huge.png", "image/png", tooBig)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("8MB");
    }

    @Test
    @DisplayName("a storage subdirectory that escapes the upload root is refused")
    void rejectsStoragePathTraversal() {
        assertThatThrownBy(() -> service.store(upload("a.png", "image/png", REAL_PNG), "../../escape"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid storage path");
    }

    @Test
    @DisplayName("a stored image keeps its bytes and lands under the month-partitioned key")
    void storesBytesUnderPartitionedKey() throws Exception {
        ImageStorageService.StoredImage stored = service.store(upload("photo.png", "image/png", REAL_PNG), "scans");

        assertThat(stored.key()).matches("scans/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.png");
        assertThat(Files.readAllBytes(uploadDir.resolve(stored.key()))).isEqualTo(REAL_PNG);
    }

    private static byte[] concat(byte[]... parts) {
        int length = 0;
        for (byte[] part : parts) {
            length += part.length;
        }
        byte[] out = new byte[length];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, out, offset, part.length);
            offset += part.length;
        }
        return out;
    }

    private static byte[] hex(String value) {
        byte[] out = new byte[value.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
