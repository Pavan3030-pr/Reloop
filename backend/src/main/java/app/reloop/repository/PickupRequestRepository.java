package app.reloop.repository;

import app.reloop.entity.PickupRequest;
import app.reloop.entity.PickupRequest.PickupStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PickupRequestRepository extends JpaRepository<PickupRequest, UUID>,
        JpaSpecificationExecutor<PickupRequest> {

    Optional<PickupRequest> findByCode(String code);

    boolean existsByCode(String code);

    Page<PickupRequest> findAllByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<PickupRequest> findAllByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, PickupStatus status, Pageable pageable);

    Page<PickupRequest> findAllByStatusOrderByCreatedAtDesc(PickupStatus status, Pageable pageable);

    Page<PickupRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Open pool: requested and not yet assigned to any collector. */
    List<PickupRequest> findAllByStatusAndCollectorIsNullOrderByCreatedAtDesc(PickupStatus status);

    /** Work assigned to one collector organisation. */
    List<PickupRequest> findAllByCollectorIdAndStatusInOrderByCreatedAtDesc(UUID collectorId,
                                                                           Collection<PickupStatus> statuses);

    Page<PickupRequest> findAllByCollectorIdOrderByCreatedAtDesc(UUID collectorId, Pageable pageable);

    long countByCollectorIdAndStatusIn(UUID collectorId, Collection<PickupStatus> statuses);

    long countByStatus(PickupStatus status);

    @Query("select count(p) from PickupRequest p where p.collector.id = :collectorId " +
            "and p.status in :statuses and p.pickedUpAt >= :since")
    long countByCollectorSince(@Param("collectorId") UUID collectorId,
                               @Param("statuses") Collection<PickupStatus> statuses,
                               @Param("since") java.time.Instant since);
}
