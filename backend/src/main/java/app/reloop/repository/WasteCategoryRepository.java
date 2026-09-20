package app.reloop.repository;

import app.reloop.entity.WasteCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WasteCategoryRepository extends JpaRepository<WasteCategory, UUID> {

    Optional<WasteCategory> findByCodeIgnoreCase(String code);

    List<WasteCategory> findAllByActiveTrueOrderByCodeAsc();
}
