package app.reloop.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks the calendar-day contract of the recycling history.
 *
 * <p>{@code from}/{@code to} arrive as bare dates (the client sends an HTML {@code <input type="date">}),
 * so the server has to decide which zone a "day" belongs to. Anchoring that to UTC looks harmless and
 * is wrong: the seeded collection network, pincodes and cities are Indian, so a household picking
 * "from today" used to lose everything they had collected between local midnight and 05:30 — those
 * instants are still on the previous UTC day. The same anchoring also made the result depend on the
 * host JVM's zone, which is how it survived review.
 *
 * <p>The fixture is deliberately placed in that gap. Both collections happen on <b>23 September in the
 * operating zone</b> while belonging to <b>22 September in UTC</b>, and the instants are absolute, so
 * the test asserts the same thing today, tomorrow, and in any host zone:
 *
 * <pre>
 *   19:15Z = 00:45 IST 23 Sep   (just after local midnight, previous UTC day)
 *   21:30Z = 03:00 IST 23 Sep   (pre-dawn, previous UTC day)
 * </pre>
 *
 * A UTC-anchored implementation returns neither row for {@code from=2026-09-23} and returns both for
 * {@code to=2026-09-22}; every assertion below therefore fails on the old behaviour and passes on the
 * zone-aware one.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "reloop.timezone=Asia/Kolkata")
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HistoryDateBoundaryTest {

    /** The operating calendar these instants are asserted against. */
    private static final String APP_ZONE = "Asia/Kolkata";

    private static final Instant JUST_AFTER_LOCAL_MIDNIGHT = Instant.parse("2026-09-22T19:15:00Z");
    private static final Instant PRE_DAWN = Instant.parse("2026-09-22T21:30:00Z");

    /** Both instants are 23 September 2026 in {@link #APP_ZONE} — the day the windows below use. */
    private static final LocalDate LOCAL_DAY = LocalDate.of(2026, 9, 23);
    private static final LocalDate PREVIOUS_LOCAL_DAY = LOCAL_DAY.minusDays(1);

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private TestRestTemplate http;
    @Autowired
    private JdbcTemplate jdbc;

    private final List<UUID> createdPickups = new java.util.ArrayList<>();
    private UUID partnerId;
    private UUID userId;
    private String residentToken;
    private String earlyCode;
    private String preDawnCode;

    @BeforeAll
    void insertBoundaryFixtures() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "boundary-" + suffix + "@reloop.test";
        register(email, "Boundary Resident");
        residentToken = login(email);
        userId = jdbc.queryForObject("select id from users where email = ?", UUID.class, email);

        String categoryId = firstCategoryId();
        String categoryCode = firstCategoryCode();
        assertThat(categoryCode).isNotBlank();

        // A VERIFIED partner with no user account: this test is about dates, not about who collected.
        partnerId = UUID.randomUUID();
        jdbc.update("""
                insert into collection_partners (id, user_id, organization_name, contact_person, phone,
                                                 address, city, pincode, operating_hours, status, verified_at)
                values (?, null, 'Boundary Test Recycling', 'Boundary Contact', '9800000000',
                        '1 Boundary Road', 'Hyderabad', '500081', 'Mon-Sat 9am-6pm', 'VERIFIED', now())
                """, partnerId);

        // 2.50 kg just after local midnight, 7.50 kg before dawn — both on 23 Sep locally.
        earlyCode = insertCollectedPickup(categoryId, new java.math.BigDecimal("2.50"), JUST_AFTER_LOCAL_MIDNIGHT);
        preDawnCode = insertCollectedPickup(categoryId, new java.math.BigDecimal("7.50"), PRE_DAWN);
    }

    @AfterAll
    void removeFixtures() {
        // Leave reloop_test as we found it: the other e2e classes assert platform-wide totals.
        createdPickups.forEach(id -> jdbc.update(
                "delete from collected_waste where pickup_request_id = ?", id));
        createdPickups.forEach(id -> jdbc.update("delete from pickup_requests where id = ?", id));
        if (partnerId != null) {
            jdbc.update("delete from collection_partners where id = ?", partnerId);
        }
    }

    @Test
    @DisplayName("a from=<local today> window includes collections made before dawn local time")
    void fromLocalTodayIncludesPreDawnCollections() {
        JsonNode history = get("/api/history?from=" + LOCAL_DAY);

        assertThat(history.path("__status").asInt()).isEqualTo(200);
        assertThat(codesIn(history)).containsExactlyInAnyOrder(earlyCode, preDawnCode);
        // The window applies to the totals too, which impact-style figures are built from.
        assertThat(history.path("totalKg").asDouble()).isEqualTo(10.00);
    }

    @Test
    @DisplayName("a window that ends on the previous local day excludes them")
    void toPreviousLocalDayExcludesThem() {
        JsonNode onlyPreviousDay = get("/api/history?from=" + PREVIOUS_LOCAL_DAY + "&to=" + PREVIOUS_LOCAL_DAY);

        assertThat(onlyPreviousDay.path("__status").asInt()).isEqualTo(200);
        assertThat(codesIn(onlyPreviousDay)).isEmpty();
        assertThat(onlyPreviousDay.path("totalKg").asDouble()).isZero();

        JsonNode upToPreviousDay = get("/api/history?to=" + PREVIOUS_LOCAL_DAY);
        assertThat(codesIn(upToPreviousDay)).isEmpty();
        assertThat(upToPreviousDay.path("totalKg").asDouble()).isZero();
    }

    @Test
    @DisplayName("both bounds resolve to the local day, so from=today&to=today returns them")
    void singleLocalDayWindowReturnsThem() {
        JsonNode singleDay = get("/api/history?from=" + LOCAL_DAY + "&to=" + LOCAL_DAY);

        assertThat(codesIn(singleDay)).containsExactlyInAnyOrder(earlyCode, preDawnCode);
        assertThat(singleDay.path("totalKg").asDouble()).isEqualTo(10.00);
    }

    @Test
    @DisplayName("a from=<local tomorrow> window is empty")
    void fromLocalTomorrowIsEmpty() {
        JsonNode tomorrow = get("/api/history?from=" + LOCAL_DAY.plusDays(1));

        assertThat(codesIn(tomorrow)).isEmpty();
        assertThat(tomorrow.path("totalKg").asDouble()).isZero();
    }

    // ------------------------------------------------------------------ fixtures

    private String insertCollectedPickup(String categoryId, java.math.BigDecimal quantityKg, Instant collectedAt) {
        UUID pickupId = UUID.randomUUID();
        String code = "RL-B" + UUID.randomUUID().toString().substring(0, 5).toUpperCase(java.util.Locale.ROOT);
        Timestamp collected = Timestamp.from(collectedAt);

        jdbc.update("""
                insert into pickup_requests (id, code, user_id, collector_id, category_id,
                                             estimated_quantity_kg, actual_quantity_kg, address, city,
                                             pincode, pickup_date, time_slot, status, picked_up_at,
                                             recovered_at, version)
                values (?, ?, ?, ?, ?, ?, ?, '1 Boundary Lane', 'Hyderabad', '500081', ?, 'MORNING',
                        'RECOVERED', ?, ?, 0)
                """, pickupId, code, userId, partnerId, UUID.fromString(categoryId),
                quantityKg, quantityKg, java.sql.Date.valueOf(LOCAL_DAY), collected, collected);

        jdbc.update("""
                insert into collected_waste (id, pickup_request_id, collector_id, user_id, category_id,
                                             quantity_kg, collection_date, notes)
                values (?, ?, ?, ?, ?, ?, ?, 'Date-boundary regression fixture')
                """, UUID.randomUUID(), pickupId, partnerId, userId, UUID.fromString(categoryId),
                quantityKg, collected);

        createdPickups.add(pickupId);
        return code;
    }

    private String firstCategoryId() {
        JsonNode categories = get("/api/waste/categories").path("__array");
        assertThat(categories.size()).as("seeded waste categories").isGreaterThanOrEqualTo(1);
        return categories.get(0).path("id").asText();
    }

    private String firstCategoryCode() {
        return get("/api/waste/categories").path("__array").get(0).path("code").asText();
    }

    // ------------------------------------------------------------------ HTTP helpers

    private void register(String email, String fullName) {
        JsonNode response = post("/api/auth/register", null,
                Map.of("email", email, "password", "Boundary#Passw0rd1", "fullName", fullName));
        assertThat(response.path("__status").asInt()).as("register %s", email).isEqualTo(201);
    }

    private String login(String email) {
        JsonNode response = post("/api/auth/login", null,
                Map.of("email", email, "password", "Boundary#Passw0rd1"));
        assertThat(response.path("__status").asInt()).as("login %s", email).isEqualTo(200);
        return response.path("accessToken").asText();
    }

    private JsonNode get(String uri) {
        HttpHeaders headers = new HttpHeaders();
        if (residentToken != null) {
            headers.setBearerAuth(residentToken);
        }
        return toNode(http.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), String.class));
    }

    private JsonNode post(String uri, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        headers.setContentType(MediaType.APPLICATION_JSON);
        return toNode(http.exchange(uri, HttpMethod.POST, new HttpEntity<>(body, headers), String.class));
    }

    private List<String> codesIn(JsonNode history) {
        assertThat(history.path("__status").asInt()).isEqualTo(200);
        return java.util.stream.StreamSupport
                .stream(history.path("entries").path("content").spliterator(), false)
                .map(node -> node.path("pickupCode").asText())
                .toList();
    }

    private JsonNode toNode(ResponseEntity<String> response) {
        String body = response.getBody() == null ? "" : response.getBody();
        com.fasterxml.jackson.databind.node.ObjectNode node = JSON.createObjectNode();
        node.put("__status", response.getStatusCode().value());
        if (!body.isBlank()) {
            try {
                JsonNode parsed = JSON.readTree(body);
                if (parsed.isArray()) {
                    node.set("__array", parsed);
                } else if (parsed.isObject()) {
                    node.setAll((com.fasterxml.jackson.databind.node.ObjectNode) parsed);
                } else {
                    node.set("__value", parsed);
                }
            } catch (Exception e) {
                throw new IllegalStateException("Non-JSON response (" + response.getStatusCode() + "): " + body, e);
            }
        }
        return node;
    }
}
