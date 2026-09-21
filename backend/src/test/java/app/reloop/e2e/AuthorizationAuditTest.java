package app.reloop.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Security and integrity audit, run against the real HTTP stack and a real PostgreSQL schema.
 *
 * <p>Each test here corresponds to a defect found in the phase-2 audit rather than to a feature:
 * <ul>
 *   <li>two collectors could both "accept" the same pickup (now an optimistic-lock conflict)</li>
 *   <li>the open pool exposed the requester's name, street address, coordinates and gate notes</li>
 *   <li>cross-object access and mutation between residents and between collectors</li>
 *   <li>RECYCLED had to become a distinct, still-terminal status</li>
 *   <li>one account could end up with two collector organisations</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthorizationAuditTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PASSWORD = "Audit#Passw0rd1";
    private static final String VICTIM_ADDRESS = "42 Private Lane, Flat 7B";
    private static final String VICTIM_NOTES = "Gate code 4417 - call before arriving";

    @Autowired
    private TestRestTemplate http;
    @Autowired
    private JdbcTemplate jdbc;

    private String categoryId;
    private String adminToken;
    private String victimToken;
    private String bystanderToken;
    private String collectorAToken;
    private String collectorBToken;

    @BeforeAll
    void setUp() {
        jdbc.execute("TRUNCATE TABLE collected_waste, pickup_requests, notifications, waste_scans, "
                + "collection_points, collection_point_materials, collection_partners, "
                + "collection_partner_materials, refresh_tokens, password_reset_tokens RESTART IDENTITY CASCADE");

        categoryId = get("/api/waste/categories", null).path("__array").get(0).path("id").asText();

        String suffix = UUID.randomUUID().toString().substring(0, 8);

        // Deliberately not relying on the ADMIN_EMAIL bootstrap: with several test contexts in one
        // run it creates the first admin only once, so which class gets one depends on run order.
        // Promoting a dedicated account keeps this suite self-sufficient and order-independent.
        String adminEmail = "audit-admin-" + suffix + "@reloop.test";
        register(adminEmail, "Audit Admin");
        jdbc.update("update users set role = 'ADMIN' where email = ?", adminEmail);
        adminToken = login(adminEmail, PASSWORD);

        victimToken = register("victim-" + suffix + "@reloop.test", "Victim Resident");
        bystanderToken = register("bystander-" + suffix + "@reloop.test", "Unrelated Resident");
        collectorAToken = verifiedCollector("collector-a-" + suffix + "@reloop.test", "Alpha Recycling");
        collectorBToken = verifiedCollector("collector-b-" + suffix + "@reloop.test", "Beta Recycling");
    }

    // ------------------------------------------------------------------ the accept race

    @Test
    @DisplayName("two collectors accepting the same request at the same instant: exactly one wins")
    void concurrentAcceptOnlyOneCollectorWins() throws Exception {
        String code = createPickup(victimToken);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch release = new CountDownLatch(1);
        try {
            List<Callable<Integer>> racers = List.of(
                    acceptRacer(collectorAToken, code, release),
                    acceptRacer(collectorBToken, code, release));
            List<Future<Integer>> futures = new ArrayList<>();
            racers.forEach(r -> futures.add(pool.submit(r)));
            release.countDown(); // both requests leave together

            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(60, TimeUnit.SECONDS));
            }

            assertThat(statuses)
                    .as("one collector must be told it won, the other must get a conflict (got %s)", statuses)
                    .containsExactlyInAnyOrder(200, 409);
        } finally {
            pool.shutdownNow();
        }

        // The database holds exactly one owner, and the resident was notified exactly once.
        Integer assigned = jdbc.queryForObject(
                "select count(*) from pickup_requests where code = ? and collector_id is not null and status = 'ACCEPTED'",
                Integer.class, code);
        assertThat(assigned).isEqualTo(1);
        Integer notifications = jdbc.queryForObject(
                "select count(*) from notifications n join pickup_requests p on p.id = n.reference_id "
                        + "where p.code = ? and n.type = 'PICKUP_ACCEPTED'", Integer.class, code);
        assertThat(notifications).as("duplicate acceptance notifications").isEqualTo(1);
    }

    @Test
    @DisplayName("the losing collector gets no phantom work: it is not in their list and they cannot touch it")
    void losingCollectorHasNothingToActOn() {
        String code = createPickup(victimToken);
        assertThat(patch("/api/collector/pickups/" + code + "/accept", collectorAToken, null)
                .path("__status").asInt()).isEqualTo(200);

        String[] collectorBTokens = {collectorBToken};
        for (String token : collectorBTokens) {
            JsonNode mine = get("/api/collector/pickups?scope=mine", token);
            assertThat(streamOf(mine.path("content")).noneMatch(n -> n.path("code").asText().equals(code)))
                    .as("an unassigned collector must not see the job as theirs")
                    .isTrue();
            assertThat(patch("/api/collector/pickups/" + code + "/schedule", token,
                    Map.of("scheduledAt", Instant.now().plusSeconds(7200).toString()))
                    .path("__status").asInt()).isEqualTo(403);
            assertThat(patch("/api/collector/pickups/" + code + "/collect", token,
                    Map.of("actualQuantityKg", "1.00")).path("__status").asInt()).isEqualTo(403);
            assertThat(patch("/api/collector/pickups/" + code + "/status", token,
                    Map.of("status", "PROCESSING")).path("__status").asInt()).isEqualTo(403);
            assertThat(patch("/api/collector/pickups/" + code + "/release", token, null)
                    .path("__status").asInt()).isEqualTo(403);
        }

        // and the request is untouched by those attempts
        assertThat(jdbc.queryForObject("select status from pickup_requests where code = ?", String.class, code))
                .isEqualTo("ACCEPTED");
    }

    // ------------------------------------------------------------------ collector privacy

    @Test
    @DisplayName("the open pool exposes no requester contact details, notes, photo or coordinates")
    void availablePoolIsRedacted() {
        String code = createPickup(victimToken);

        JsonNode pool = get("/api/collector/pickups?scope=available", collectorBToken);
        assertThat(pool.path("__status").asInt()).isEqualTo(200);
        JsonNode summary = streamOf(pool.path("content"))
                .filter(n -> n.path("code").asText().equals(code)).findFirst()
                .orElseThrow(() -> new AssertionError("new request missing from the available pool"));

        Set<String> fields = new HashSet<>();
        summary.fieldNames().forEachRemaining(fields::add);

        assertThat(fields)
                .as("collectors must not receive a household's precise details before assignment")
                .containsExactlyInAnyOrder("code", "status", "category", "estimatedQuantityKg",
                        "city", "pickupDate", "timeSlot", "createdAt", "approximateDistanceKm");
        assertThat(fields).doesNotContain("address", "requesterName", "notes", "photoUrl",
                "latitude", "longitude", "pincode");
        assertThat(summary.toString()).doesNotContain(VICTIM_ADDRESS, VICTIM_NOTES);

        // still useful: the material, the city and a coarse distance for judging the trip
        assertThat(summary.path("city").asText()).isEqualTo("Hyderabad");
        assertThat(summary.path("category").path("code").asText()).isNotBlank();

        JsonNode withLocation = get("/api/collector/pickups?scope=available&lat=17.49&lng=78.39", collectorBToken);
        JsonNode located = streamOf(withLocation.path("content"))
                .filter(n -> n.path("code").asText().equals(code)).findFirst().orElseThrow();
        assertThat(located.path("approximateDistanceKm").asInt())
                .as("distance is rounded to whole kilometres on purpose")
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("full detail is released to the assigned collector only, and to no one else")
    void detailUnlocksOnlyForTheAssignedCollector() {
        String code = createPickup(victimToken);
        assertThat(patch("/api/collector/pickups/" + code + "/accept", collectorAToken, null)
                .path("__status").asInt()).isEqualTo(200);

        JsonNode assignedView = get("/api/pickups/" + code, collectorAToken);
        assertThat(assignedView.path("__status").asInt()).isEqualTo(200);
        assertThat(assignedView.path("address").asText()).isEqualTo(VICTIM_ADDRESS);
        assertThat(assignedView.path("notes").asText()).isEqualTo(VICTIM_NOTES);

        assertThat(get("/api/pickups/" + code, collectorBToken).path("__status").asInt()).isEqualTo(403);
        assertThat(get("/api/pickups/" + code, bystanderToken).path("__status").asInt()).isEqualTo(403);
        assertThat(get("/api/pickups/" + code, victimToken).path("__status").asInt()).isEqualTo(200);

        // an assigned request leaves the open pool
        JsonNode pool = get("/api/collector/pickups?scope=available", collectorBToken);
        assertThat(streamOf(pool.path("content")).noneMatch(n -> n.path("code").asText().equals(code))).isTrue();
    }

    // ------------------------------------------------------------------ object-level authorization

    @Test
    @DisplayName("a resident cannot read, cancel or guess another resident's request")
    void residentsAreIsolatedFromEachOther() {
        String code = createPickup(victimToken);

        assertThat(get("/api/pickups/" + code, bystanderToken).path("__status").asInt()).isEqualTo(403);
        assertThat(patch("/api/pickups/" + code + "/cancel", bystanderToken, null).path("__status").asInt()).isEqualTo(403);
        assertThat(get("/api/pickups/" + code, victimToken).path("__status").asInt()).isEqualTo(200);

        JsonNode mine = get("/api/pickups", bystanderToken);
        assertThat(streamOf(mine.path("content")).noneMatch(n -> n.path("code").asText().equals(code))).isTrue();

        // scans, history and impact are scoped to the caller
        JsonNode bystanderScans = get("/api/waste/scans", bystanderToken);
        assertThat(bystanderScans.path("__status").asInt()).isEqualTo(200);
        assertThat(streamOf(bystanderScans.path("content"))).isEmpty();
        assertThat(get("/api/impact", bystanderToken).path("totalCollectedKg").asDouble()).isZero();
    }

    @Test
    @DisplayName("collector and admin surfaces reject the wrong roles")
    void roleBoundariesHold() {
        for (String path : List.of(
                "/api/collector/dashboard",
                "/api/collector/pickups?scope=mine",
                "/api/collector/pickups?scope=available")) {
            assertThat(get(path, bystanderToken).path("__status").asInt())
                    .as("USER must not reach %s", path).isEqualTo(403);
            assertThat(get(path, victimToken).path("__status").asInt()).isEqualTo(403);
            assertThat(get(path, collectorAToken).path("__status").asInt()).isEqualTo(200);
        }

        for (String path : List.of("/api/admin/users", "/api/admin/collectors", "/api/admin/pickups",
                "/api/admin/analytics")) {
            assertThat(get(path, bystanderToken).path("__status").asInt())
                    .as("USER must not reach %s", path).isEqualTo(403);
            assertThat(get(path, collectorAToken).path("__status").asInt())
                    .as("COLLECTOR must not reach %s", path).isEqualTo(403);
            assertThat(get(path, adminToken).path("__status").asInt()).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("a collector cannot read another collector's organisation or application")
    void unverifiedAccountsCannotMasqueradeAsCollectors() {
        String pendingEmail = "pending-" + UUID.randomUUID().toString().substring(0, 8) + "@reloop.test";
        String pendingToken = register(pendingEmail, "Pending Applicant");
        assertThat(post("/api/collectors/apply", pendingToken, application("Pending Cooperative"))
                .path("__status").asInt()).isEqualTo(201);

        // Role is still USER, so the collector surface is closed even though an application exists.
        assertThat(get("/api/collector/dashboard", pendingToken).path("__status").asInt()).isEqualTo(403);
    }

    // ------------------------------------------------------------------ recovery vocabulary

    @Test
    @DisplayName("PROCESSING can end as RECYCLED, and RECYCLED is terminal")
    void recycledIsADistinctTerminalState() {
        String code = createPickup(victimToken);

        // A verified collector who has not been assigned this request is refused outright (403) —
        // the assignment check deliberately runs before any transition is even considered.
        assertThat(patch("/api/collector/pickups/" + code + "/status", collectorAToken,
                Map.of("status", "RECYCLED")).path("__status").asInt()).isEqualTo(403);

        assertThat(patch("/api/collector/pickups/" + code + "/accept", collectorAToken, null)
                .path("__status").asInt()).isEqualTo(200);
        assertThat(patch("/api/collector/pickups/" + code + "/collect", collectorAToken,
                Map.of("actualQuantityKg", "4.25")).path("__status").asInt()).isEqualTo(200);

        // PICKED_UP → RECYCLED must still go through PROCESSING
        assertThat(patch("/api/collector/pickups/" + code + "/status", collectorAToken,
                Map.of("status", "RECYCLED")).path("__status").asInt()).isEqualTo(409);

        assertThat(patch("/api/collector/pickups/" + code + "/status", collectorAToken,
                Map.of("status", "PROCESSING")).path("__status").asInt()).isEqualTo(200);
        JsonNode recycled = patch("/api/collector/pickups/" + code + "/status", collectorAToken,
                Map.of("status", "RECYCLED"));
        assertThat(recycled.path("__status").asInt()).isEqualTo(200);
        assertThat(recycled.path("status").asText()).isEqualTo("RECYCLED");
        assertThat(recycled.path("recoveredAt").isNull()).isFalse();

        // terminal: nothing may follow, and the weight is preserved
        assertThat(patch("/api/collector/pickups/" + code + "/status", collectorAToken,
                Map.of("status", "RECOVERED")).path("__status").asInt()).isEqualTo(409);
        assertThat(patch("/api/collector/pickups/" + code + "/collect", collectorAToken,
                Map.of("actualQuantityKg", "9.99")).path("__status").asInt()).isEqualTo(409);
        assertThat(patch("/api/pickups/" + code + "/cancel", victimToken, null).path("__status").asInt())
                .isEqualTo(409);

        assertThat(jdbc.queryForObject("select actual_quantity_kg from pickup_requests where code = ?",
                java.math.BigDecimal.class, code)).isEqualByComparingTo("4.25");

        // the resident's history reflects the measured weight
        JsonNode history = get("/api/history", victimToken);
        assertThat(history.path("__status").asInt()).isEqualTo(200);
        assertThat(history.path("totalKg").asDouble()).isEqualTo(4.25);
    }

    @Test
    @DisplayName("an unknown or unsupported status value is a clear 400, not a 500")
    void unknownStatusIsRejectedCleanly() {
        String code = createPickup(victimToken);
        patch("/api/collector/pickups/" + code + "/accept", collectorAToken, null);

        assertThat(patch("/api/collector/pickups/" + code + "/status", collectorAToken,
                Map.of("status", "NOT_A_STATUS")).path("__status").asInt()).isEqualTo(400);
        assertThat(patch("/api/collector/pickups/" + code + "/status", collectorAToken,
                Map.of("status", "CANCELLED")).path("__status").asInt()).isEqualTo(400);
    }

    // ------------------------------------------------------------------ collector applications

    @Test
    @DisplayName("one account can hold only one collector organisation")
    void duplicatePartnerApplicationIsRejected() {
        String email = "dupe-" + UUID.randomUUID().toString().substring(0, 8) + "@reloop.test";
        String token = register(email, "Dupe Applicant");

        assertThat(post("/api/collectors/apply", token, application("First Cooperative"))
                .path("__status").asInt()).isEqualTo(201);
        assertThat(post("/api/collectors/apply", token, application("Second Cooperative"))
                .path("__status").asInt()).isEqualTo(409);

        assertThat(jdbc.queryForObject(
                "select count(*) from collection_partners p join users u on u.id = p.user_id where u.email = ?",
                Integer.class, email)).isEqualTo(1);
    }

    @Test
    @DisplayName("simultaneous duplicate applications still create exactly one organisation")
    void concurrentApplicationsCannotDuplicate() throws Exception {
        String email = "race-" + UUID.randomUUID().toString().substring(0, 8) + "@reloop.test";
        String token = register(email, "Race Applicant");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch release = new CountDownLatch(1);
        try {
            List<Callable<Integer>> racers = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                String org = "Race Cooperative " + i;
                racers.add(() -> {
                    release.await();
                    return post("/api/collectors/apply", token, application(org)).path("__status").asInt();
                });
            }
            List<Future<Integer>> futures = new ArrayList<>();
            racers.forEach(r -> futures.add(pool.submit(r)));
            release.countDown();

            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(60, TimeUnit.SECONDS));
            }
            assertThat(statuses).as("got %s", statuses).containsAnyOf(201);
            assertThat(statuses).as("got %s", statuses).doesNotContain(500);
        } finally {
            pool.shutdownNow();
        }

        assertThat(jdbc.queryForObject(
                "select count(*) from collection_partners p join users u on u.id = p.user_id where u.email = ?",
                Integer.class, email))
                .as("the unique index must hold even when both requests race")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("verification stays an admin action")
    void collectorsCannotVerifyThemselves() {
        String email = "selfverify-" + UUID.randomUUID().toString().substring(0, 8) + "@reloop.test";
        String token = register(email, "Self Verifier");
        String partnerId = post("/api/collectors/apply", token, application("Self Verified Ltd"))
                .path("id").asText();

        assertThat(patch("/api/admin/collectors/" + partnerId + "/verify", token, null)
                .path("__status").asInt()).isEqualTo(403);
        assertThat(patch("/api/admin/collectors/" + partnerId + "/verify", collectorAToken, null)
                .path("__status").asInt()).isEqualTo(403);

        assertThat(patch("/api/admin/collectors/" + partnerId + "/verify", adminToken, null)
                .path("__status").asInt()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select status from collection_partners where id = ?::uuid",
                String.class, partnerId)).isEqualTo("VERIFIED");
    }

    // ------------------------------------------------------------------ helpers

    private Callable<Integer> acceptRacer(String token, String code, CountDownLatch release) {
        return () -> {
            release.await();
            return patch("/api/collector/pickups/" + code + "/accept", token, null).path("__status").asInt();
        };
    }

    private Map<String, Object> application(String organisation) {
        return Map.of(
                "organizationName", organisation,
                "contactPerson", "Audit Contact",
                "phone", "9888888888",
                "address", "9 Industrial Estate",
                "city", "Hyderabad",
                "pincode", "500081",
                "operatingHours", "Mon-Sat 9am-6pm",
                "registrationNumber", "TS-AUDIT-0001",
                "materialCodes", List.of("PLASTIC", "CARDBOARD"));
    }

    /** Registers, applies as a collector, gets admin-verified, then re-logs in for a COLLECTOR token. */
    private String verifiedCollector(String email, String organisation) {
        register(email, organisation + " Contact");
        String token = login(email, PASSWORD);
        String partnerId = post("/api/collectors/apply", token, application(organisation)).path("id").asText();
        assertThat(patch("/api/admin/collectors/" + partnerId + "/verify", adminToken, null)
                .path("__status").asInt()).isEqualTo(200);
        // The role is baked into the JWT, so a fresh token is required after promotion.
        return login(email, PASSWORD);
    }

    private String createPickup(String token) {
        JsonNode created = postMultipart("/api/pickups", token, Map.of(
                "categoryId", categoryId,
                "estimatedQuantityKg", "2.50",
                "address", VICTIM_ADDRESS,
                "city", "Hyderabad",
                "pincode", "500081",
                "latitude", "17.4483",
                "longitude", "78.3908",
                "pickupDate", LocalDate.now().plusDays(1).toString(),
                "timeSlot", "MORNING",
                "notes", VICTIM_NOTES));
        assertThat(created.path("__status").asInt()).isEqualTo(201);
        return created.path("code").asText();
    }

    private String register(String email, String fullName) {
        assertThatCode(() -> post("/api/auth/register", null, Map.of(
                "email", email, "password", PASSWORD, "fullName", fullName, "phone", "9000000001")))
                .doesNotThrowAnyException();
        return login(email, PASSWORD);
    }

    private String login(String email, String password) {
        JsonNode response = post("/api/auth/login", null, Map.of("email", email, "password", password));
        assertThat(response.path("__status").asInt()).as("login for %s", email).isEqualTo(200);
        return response.path("accessToken").asText();
    }

    private JsonNode exchange(HttpMethod method, String uri, Object body) {
        return toNode(http.exchange(uri, method, new HttpEntity<>(body, new HttpHeaders()), String.class));
    }

    private HttpHeaders auth(String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }

    private JsonNode get(String uri, String token) {
        return toNode(http.exchange(uri, HttpMethod.GET, new HttpEntity<>(auth(token)), String.class));
    }

    private JsonNode post(String uri, String token, Object body) {
        HttpHeaders headers = auth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return toNode(http.exchange(uri, HttpMethod.POST, new HttpEntity<>(body, headers), String.class));
    }

    private JsonNode patch(String uri, String token, Object body) {
        HttpHeaders headers = auth(token);
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return toNode(http.exchange(uri, HttpMethod.PATCH, new HttpEntity<>(body, headers), String.class));
    }

    @SuppressWarnings("unchecked")
    private JsonNode postMultipart(String uri, String token, Map<String, Object> fields) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        fields.forEach((key, value) -> body.add(key, value == null ? "" : value.toString()));
        HttpHeaders headers = auth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return toNode(http.exchange(uri, HttpMethod.POST, new HttpEntity<>(body, headers), String.class));
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

    private static java.util.stream.Stream<JsonNode> streamOf(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return java.util.stream.Stream.empty();
        }
        return java.util.stream.StreamSupport.stream(arrayNode.spliterator(), false);
    }
}
