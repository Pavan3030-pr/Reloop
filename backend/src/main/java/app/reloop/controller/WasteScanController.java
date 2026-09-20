package app.reloop.controller;

import app.reloop.dto.scan.SaveScanRequest;
import app.reloop.dto.scan.ScanDto;
import app.reloop.dto.scan.WasteAnalysisResponse;
import app.reloop.security.SecurityUtils;
import app.reloop.service.WasteScanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/waste")
@RequiredArgsConstructor
public class WasteScanController {

    private final WasteScanService wasteScanService;

    /**
     * AI analysis endpoint. Called only when the user presses "Analyze".
     * Returns 503 with a clear message when Gemini is unavailable so the client
     * can fall back to manual classification.
     */
    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public WasteAnalysisResponse analyze(@RequestParam("image") MultipartFile image) {
        return wasteScanService.analyze(image);
    }

    @PostMapping(value = "/scans", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ScanDto saveScan(@Valid @ModelAttribute SaveScanRequest request,
                            @RequestParam(value = "image", required = false) MultipartFile image) {
        return wasteScanService.save(SecurityUtils.currentUser(), request, image);
    }

    @GetMapping("/scans")
    public Page<ScanDto> listScans(@RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size) {
        return wasteScanService.listMine(SecurityUtils.currentUser(), page, size);
    }

    @GetMapping("/scans/{id}")
    public ScanDto getScan(@PathVariable UUID id) {
        return wasteScanService.getMine(SecurityUtils.currentUser(), id);
    }
}
