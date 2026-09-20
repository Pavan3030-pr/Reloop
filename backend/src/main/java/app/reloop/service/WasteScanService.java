package app.reloop.service;

import app.reloop.config.AppProperties;
import app.reloop.dto.scan.SaveScanRequest;
import app.reloop.dto.scan.ScanDto;
import app.reloop.dto.scan.WasteAnalysisResponse;
import app.reloop.entity.User;
import app.reloop.entity.WasteCategory;
import app.reloop.entity.WasteScan;
import app.reloop.exception.NotFoundException;
import app.reloop.integration.GeminiService;
import app.reloop.repository.WasteCategoryRepository;
import app.reloop.repository.WasteScanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WasteScanService {

    private final WasteScanRepository wasteScanRepository;
    private final WasteCategoryRepository wasteCategoryRepository;
    private final GeminiService geminiService;
    private final ImageStorageService imageStorageService;
    private final AppProperties properties;

    /**
     * Runs AI analysis on an uploaded image. Nothing is persisted here; the client
     * shows the result and the user decides whether to save it (possibly corrected).
     */
    public WasteAnalysisResponse analyze(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new app.reloop.exception.BadRequestException("Upload an image of the waste item first");
        }
        // Reject non-images before spending a provider call, and trust the type detected from the
        // file's content rather than the one the client declared.
        String detectedMime = imageStorageService.detectImageType(image);
        GeminiService.GeminiAnalysis analysis = geminiService.analyze(safeBytes(image), detectedMime);
        WasteCategory category = resolveCategory(analysis.category());
        BigDecimal confidence = BigDecimal.valueOf(analysis.confidence()).setScale(3, RoundingMode.HALF_UP);
        return new WasteAnalysisResponse(
                analysis.item(),
                category != null ? category.getCode() : "OTHER",
                category != null ? category.getName() : "Other",
                confidence,
                analysis.recyclable(),
                analysis.hazardous(),
                analysis.disposalInstruction(),
                analysis.lowConfidence(),
                properties.gemini() != null ? properties.gemini().model() : null,
                null);
    }

    @Transactional
    public ScanDto save(User user, SaveScanRequest request, MultipartFile image) {
        if (request.categoryId() == null) {
            throw new app.reloop.exception.BadRequestException("Select a waste category for this item");
        }
        WasteCategory category = wasteCategoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new NotFoundException("Unknown waste category"));
        if (!category.isActive()) {
            throw new app.reloop.exception.BadRequestException("This waste category is no longer available");
        }

        boolean aiAssisted = request.confidence() != null;
        WasteScan scan = new WasteScan();
        scan.setUser(user);
        scan.setCategory(category);
        scan.setDetectedItem(request.detectedItem() == null ? category.getName() : request.detectedItem().trim());
        scan.setConfidence(aiAssisted ? request.confidence().setScale(3, RoundingMode.HALF_UP) : null);
        scan.setRecyclable(request.recyclable());
        scan.setHazardous(request.hazardous());
        scan.setDisposalInstruction(request.disposalInstruction());
        scan.setSource(aiAssisted ? WasteScan.ScanSource.AI : WasteScan.ScanSource.MANUAL);
        scan.setAiModel(aiAssisted ? (properties.gemini() != null ? properties.gemini().model() : null) : null);
        scan.setRawResponse(request.aiRawResponse());

        if (image != null && !image.isEmpty()) {
            ImageStorageService.StoredImage stored = imageStorageService.store(image, "scans");
            scan.setImageUrl(stored.publicUrl());
        }
        return toDto(wasteScanRepository.save(scan));
    }

    @Transactional(readOnly = true)
    public Page<ScanDto> listMine(User user, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 50));
        return wasteScanRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId(), pageable)
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public ScanDto getMine(User user, UUID id) {
        WasteScan scan = wasteScanRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Scan not found"));
        if (!scan.getUser().getId().equals(user.getId())
                && user.getRole() != app.reloop.security.Role.ADMIN) {
            throw new app.reloop.exception.ForbiddenException("You can only view your own scans");
        }
        return toDto(scan);
    }

    private WasteCategory resolveCategory(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return wasteCategoryRepository.findByCodeIgnoreCase(normalized)
                .or(() -> wasteCategoryRepository.findByCodeIgnoreCase(mapSynonym(normalized)))
                .orElse(null);
    }

    private String mapSynonym(String code) {
        return switch (code) {
            case "EWASTE", "ELECTRONIC", "ELECTRONICS", "ELECTRONIC_WASTE" -> "E_WASTE";
            case "FOOD", "FOOD_WASTE", "COMPOST", "ORGANICS" -> "ORGANIC";
            case "HAZARD", "HAZARDOUS_WASTE" -> "HAZARDOUS";
            case "CARDBOARD_BOX", "BOX" -> "CARDBOARD";
            case "BOTTLE", "PLASTICS" -> "PLASTIC";
            default -> code;
        };
    }

    private byte[] safeBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (Exception e) {
            throw new app.reloop.exception.BadRequestException("Could not read the uploaded image");
        }
    }

    private ScanDto toDto(WasteScan scan) {
        return new ScanDto(
                scan.getId(),
                scan.getImageUrl(),
                scan.getDetectedItem(),
                scan.getCategory() != null ? app.reloop.dto.waste.WasteCategoryDto.from(scan.getCategory()) : null,
                scan.getConfidence(),
                scan.getRecyclable(),
                scan.getHazardous(),
                scan.getDisposalInstruction(),
                scan.getSource() != null ? scan.getSource().name() : null,
                scan.getCreatedAt());
    }
}
