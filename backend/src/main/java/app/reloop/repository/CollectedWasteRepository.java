package app.reloop.repository;

import app.reloop.entity.CollectedWaste;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CollectedWasteRepository extends JpaRepository<CollectedWaste, UUID> {

    boolean existsByPickupRequestId(UUID pickupRequestId);

    long countByUserId(UUID userId);

    @Query("""
            select cw from CollectedWaste cw
            join cw.pickupRequest pr
            join cw.category c
            where cw.user.id = :userId
              and (cast(:categoryCode as string) is null or c.code = cast(:categoryCode as string))
              and (cast(:status as string) is null or cast(pr.status as string) = cast(:status as string))
              and cw.collectionDate >= coalesce(:from, cw.collectionDate)
              and cw.collectionDate <= coalesce(:to, cw.collectionDate)
            order by cw.collectionDate desc
            """)
    Page<CollectedWaste> searchHistory(@Param("userId") UUID userId,
                                       @Param("categoryCode") String categoryCode,
                                       @Param("status") String status,
                                       @Param("from") Instant from,
                                       @Param("to") Instant to,
                                       Pageable pageable);

    @Query("""
            select coalesce(sum(cw.quantityKg), 0) from CollectedWaste cw
            join cw.pickupRequest pr
            join cw.category c
            where cw.user.id = :userId
              and (cast(:categoryCode as string) is null or c.code = cast(:categoryCode as string))
              and (cast(:status as string) is null or cast(pr.status as string) = cast(:status as string))
              and cw.collectionDate >= coalesce(:from, cw.collectionDate)
              and cw.collectionDate <= coalesce(:to, cw.collectionDate)
            """)
    BigDecimal sumKgForUser(@Param("userId") UUID userId,
                            @Param("categoryCode") String categoryCode,
                            @Param("status") String status,
                            @Param("from") Instant from,
                            @Param("to") Instant to);

    interface CategoryTotal {
        String getCode();

        String getName();

        BigDecimal getKg();
    }

    @Query("""
            select c.code as code, c.name as name, coalesce(sum(cw.quantityKg), 0) as kg
            from CollectedWaste cw
            join cw.category c
            where cw.user.id = :userId
            group by c.code, c.name
            order by kg desc
            """)
    List<CategoryTotal> sumKgByCategoryForUser(@Param("userId") UUID userId);

    @Query("select coalesce(sum(cw.quantityKg), 0) from CollectedWaste cw where cw.collector.id = :collectorId")
    BigDecimal sumKgByCollector(@Param("collectorId") UUID collectorId);

    @Query("select count(cw) from CollectedWaste cw where cw.collector.id = :collectorId")
    long countByCollector(@Param("collectorId") UUID collectorId);

    @Query("select coalesce(sum(cw.quantityKg), 0) from CollectedWaste cw")
    BigDecimal sumAllKg();

    @Query("select count(cw) from CollectedWaste cw")
    long countAll();

    interface AdminCategoryTotal {
        String getCode();

        String getName();

        BigDecimal getKg();
    }

    @Query("""
            select c.code as code, c.name as name, coalesce(sum(cw.quantityKg), 0) as kg
            from CollectedWaste cw
            join cw.category c
            group by c.code, c.name
            order by kg desc
            """)
    List<AdminCategoryTotal> sumAllKgByCategory();
}
