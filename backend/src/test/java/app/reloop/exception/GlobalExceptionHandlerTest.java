package app.reloop.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.StaleObjectStateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks the concurrency-conflict contract: both the Spring optimistic-lock type and the Hibernate
 * stale-state type resolve to a clean 409 whose payload carries an actionable message and, crucially,
 * none of the internal detail (exception class, entity name, SQL, stack) that produced it.
 */
class GlobalExceptionHandlerTest {

    // findAndRegisterModules() matches what Spring Boot's auto-configured ObjectMapper does
    // (notably the JavaTime module, so the Instant timestamp serializes).
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("a lost @Version update on a pickup becomes 409 with an actionable, detail-free payload")
    void springOptimisticLockFailureBecomes409() throws Exception {
        // This is what two collectors accepting the same pickup at the same instant produces.
        ObjectOptimisticLockingFailureException ex =
                new ObjectOptimisticLockingFailureException("app.reloop.entity.PickupRequest", "RL-123456");

        var response = handler.handlePickupClaimConflict(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(409);
        assertThat(body.error()).isEqualTo("Conflict");
        assertThat(body.message())
                .contains("already been updated or claimed")
                .contains("refresh your dashboard");

        // Requirement: nothing internal may reach the client through this wrapper.
        String json = JSON.writeValueAsString(body);
        assertThat(json)
                .doesNotContain("Exception")
                .doesNotContain("OptimisticLock")
                .doesNotContain("PickupRequest")
                .doesNotContain("StackTrace")
                .doesNotContain("app.reloop")
                .doesNotContain("SQL");
    }

    @Test
    @DisplayName("a Hibernate stale-state exception takes exactly the same 409 path")
    void hibernateStaleObjectStateBecomes409() {
        StaleObjectStateException ex =
                new StaleObjectStateException("app.reloop.entity.PickupRequest", "RL-123456");

        var response = handler.handlePickupClaimConflict(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("refresh your dashboard");
    }
}
