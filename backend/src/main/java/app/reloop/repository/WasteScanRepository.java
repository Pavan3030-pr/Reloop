package app.reloop.repository;

import app.reloop.entity.WasteScan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WasteScanRepository extends JpaRepository<WasteScan, UUID> {

    Page<WasteScan> findAllByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
