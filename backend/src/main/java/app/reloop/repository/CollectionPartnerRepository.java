package app.reloop.repository;

import app.reloop.entity.CollectionPartner;
import app.reloop.entity.PartnerStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CollectionPartnerRepository extends JpaRepository<CollectionPartner, UUID> {

    Optional<CollectionPartner> findByUserId(UUID userId);

    List<CollectionPartner> findAllByStatus(PartnerStatus status);

    @Query("select p from CollectionPartner p where p.status = :status order by p.createdAt desc")
    Page<CollectionPartner> searchByStatusAdmin(@Param("status") PartnerStatus status, Pageable pageable);

    Page<CollectionPartner> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
