package app.reloop.repository;

import app.reloop.entity.RefreshToken;
import app.reloop.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update RefreshToken rt set rt.revokedAt = :now where rt.user = :user and rt.revokedAt is null")
    void revokeAllForUser(@Param("user") User user, @Param("now") Instant now);

    @Modifying
    @Query("delete from RefreshToken rt where rt.expiresAt < :now")
    void deleteExpired(@Param("now") Instant now);
}
