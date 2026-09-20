package app.reloop.repository;

import app.reloop.entity.CollectionPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CollectionPointRepository extends JpaRepository<CollectionPoint, UUID> {

    @Query("""
            select distinct p from CollectionPoint p
            left join p.materials m
            where p.active = true
              and (cast(:materialCode as string) is null or m.code = cast(:materialCode as string))
              and (cast(:city as string) is null or lower(p.city) = lower(cast(:city as string)))
              and (cast(:query as string) is null
                   or lower(p.name) like lower(concat('%', cast(:query as string), '%'))
                   or lower(p.address) like lower(concat('%', cast(:query as string), '%'))
                   or lower(p.city) like lower(concat('%', cast(:query as string), '%')))
            order by p.name asc
            """)
    List<CollectionPoint> search(@Param("materialCode") String materialCode,
                                 @Param("city") String city,
                                 @Param("query") String query);
}
