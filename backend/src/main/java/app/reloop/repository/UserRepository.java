package app.reloop.repository;

import app.reloop.entity.User;
import app.reloop.security.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByRole(Role role);

    /**
     * Admin directory search over email and profile name. A null/blank query returns every
     * user; the cast keeps PostgreSQL able to infer the bind type when the query is absent.
     */
    @Query(value = """
            select u from User u
            left join UserProfile p on p.user = u
            where (cast(:query as string) is null
                   or lower(u.email) like lower(concat('%', cast(:query as string), '%'))
                   or lower(p.fullName) like lower(concat('%', cast(:query as string), '%')))
            order by u.createdAt desc
            """,
            countQuery = """
            select count(u) from User u
            left join UserProfile p on p.user = u
            where (cast(:query as string) is null
                   or lower(u.email) like lower(concat('%', cast(:query as string), '%'))
                   or lower(p.fullName) like lower(concat('%', cast(:query as string), '%')))
            """)
    Page<User> searchDirectory(@Param("query") String query, Pageable pageable);
}
