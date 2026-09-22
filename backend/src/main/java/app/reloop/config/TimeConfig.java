package app.reloop.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.ZoneId;

/**
 * The application clock.
 *
 * <p>Every calendar-day boundary the product exposes to a person — the "from"/"to" dates on the
 * recycling history, the collector's "today" figure — has to be resolved in one agreed zone. Two
 * wrong answers are easy to reach for and both are bugs:
 *
 * <ul>
 *   <li><b>UTC</b> (what this code used to do): a household in +05:30 picking "from today" silently
 *       loses everything they collected between midnight and 05:30, because those instants fall on
 *       the previous UTC day.</li>
 *   <li><b>The JVM default</b>: the same request returns different rows on a laptop in IST and in a
 *       container that defaults to UTC, which is how the defect above stayed hidden.</li>
 * </ul>
 *
 * <p>ReLoop runs a single operating calendar, configured explicitly by {@code reloop.timezone} /
 * {@code RELOOP_TIMEZONE} (default {@code Asia/Kolkata} — the seeded collection network, pincodes and
 * city list are Indian). Instants themselves are still stored in UTC
 * ({@code hibernate.jdbc.time_zone: UTC}); only the interpretation of a bare calendar date depends on
 * this zone. An unusable zone id fails startup rather than quietly falling back.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock appClock(@Value("${reloop.timezone:}") String timezone) {
        if (timezone == null || timezone.isBlank()) {
            // No override configured: still better than UTC-anchored day boundaries, but deployments
            // should set RELOOP_TIMEZONE so the calendar does not follow the host.
            return Clock.systemDefaultZone();
        }
        try {
            return Clock.system(ZoneId.of(timezone.trim()));
        } catch (DateTimeException | IllegalArgumentException e) {
            throw new IllegalStateException(
                    "reloop.timezone is not a valid time zone id: '" + timezone
                            + "'. Use an IANA id such as Asia/Kolkata (see backend/.env.example).", e);
        }
    }
}
