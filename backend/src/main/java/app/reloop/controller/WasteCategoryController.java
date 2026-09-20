package app.reloop.controller;

import app.reloop.dto.waste.WasteCategoryDto;
import app.reloop.repository.WasteCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/waste")
@RequiredArgsConstructor
public class WasteCategoryController {

    private final WasteCategoryRepository wasteCategoryRepository;

    @GetMapping("/categories")
    public List<WasteCategoryDto> listCategories() {
        return wasteCategoryRepository.findAllByActiveTrueOrderByCodeAsc().stream()
                .map(WasteCategoryDto::from)
                .toList();
    }
}
