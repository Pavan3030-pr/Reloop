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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Regression cover for the open collector pool.
 *
 * <p>Two properties have to hold at once, and they pull in opposite directions: a collector must be
 * able to find the work they can actually serve (city, material, distance), and must <em>not</em> be
 * handed the details of every household on the platform while doing so. Every assertion below is
 * therefore paired — a filter narrows the pool, and the narrowed pool still leaks nothing.
 *
 * <p>Filtering is asserted against real HTTP responses and real SQL, not against a service mock, so
 * the specification predicates are genuinely exercised.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CollectorPoolFilterTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PASSWORD = "Pool#Passw0rd1";
    private static final String RESIDENT_NOTES = "Gate code 4417 - call before arriving";

    /** The collector's own position, used to measure the coarse distance to each request. */
    private static final String CALLER_LAT = "17.4483";
    private static final String CALLER_LNG = "78.3908";

    /** Roughly 520 km from the caller — far outside any sane local radius. */
    private static final String FAR_LAT = "13.0827";
    private static final String FAR_LNG = "80.2707";

    @Autowired
    private TestRestTemplate http;
    @Autowired
    private JdbcTemplate jdbc;

    private String categoryAId;
    private String categoryACode;
    private String categoryBId;
    private String categoryBCode;

    private String adminToken;
    private String residentToken;
    private String collectorToken;
    private String pendingCollectorToken;

    private String hyderabadA;
    private String hyderabadB;
    private String chennaiA;

    @BeforeAll
    void setUp() {
        jdbc.execute("TRUNCATE TABLE collected_waste, pickup_requests, notifications, waste_scans, "
                + "collection_points, collection_point_materials, collection_partners, "
                + "collection_partner_materials, refresh_tokens, password_reset_tokens RESTART IDENTITY CASCADE");

        JsonNode categories = get("/api/waste/categories", null).path("__array");
        assertThat(categories.size()).as("seeded waste categories").isGreaterThanOrEqualTo(2);
        categoryAId = categories.get(0).path("id").asText();
        categoryACode = categories.get(0).path("code").asText();
        categoryBId = categories.get(1).path("id").asText();
        categoryBCode = categories.get(1).path("code").asText();

        String suffix = UUID.randomUUID().toString().substring(0, 8);

        // Self-sufficient admin, for the same reason as AuthorizationAuditTest: the bootstrap
        // account is created only when no admin exists yet, so which class receives it depends on
        // run order.
        String adminEmail = "pool-admin-" + suffix + "@reloop.test";
        register(adminEmail, "Pool Admin");
        jdbc.update("update users set role = 'ADMIN' where email = ?", adminEmail);
        adminToken = login(adminEmail, PASSWORD);

        residentToken = register("pool-resident-" + suffix + "@reloop.test", "Pool Resident");
        collectorToken = verifiedCollector("pool-collector-" + suffix + "@reloop.test", "Gamma Recycling");

        // Applied but not approved: still role USER, so the collector routes must refuse it.
        String pendingEmail = "pool-pending-" + suffix + "@reloop.test";
        register(pendingEmail, "Pending Recycling");
        String pendingToken = login(pendingEmail, PASSWORD);
        post("/api/collectors/apply", pendingToken, application("Pending Recycling"));
        pendingCollectorToken = login(pendingEmail, PASSWORD);

        hyderabadA = createPickup(categoryAId, "Hyderabad", "500081", CALLER_LAT, CALLER_LNG);
        hyderabadB = createPickup(categoryBId, "Hyderabad", "500081", CALLER_LAT, CALLER_LNG);
        chennaiA = createPickup(categoryAId, "Chennai", "600001", FAR_LAT, FAR_LNG);
    }

    // ------------------------------------------------------------------ filtering narrows the pool

    @Test
    @DisplayName("the filter options are the cities and materials that really have open requests")
    void filtersOfferOnlyValuesPresentInThePool() {
        JsonNode filters = get("/api/collector/pickups/filters", collectorToken);
        assertThat(filters.path("__status").asInt()).isEqualTo(200);

        assertThat(textList(filters.path("cities"))).containsExactlyInAnyOrder("Hyderabad", "Chennai");
        assertThat(textList(filters.path("materialCodes")))
                .containsExactlyInAnyOrder(categoryACode, categoryBCode);
    }

    @Test
    @DisplayName("filtering by city returns only that city, case-insensitively")
    void cityFilterNarrowsToThatCity() {
        JsonNode page = get("/api/collector/pickups?city=chennai&size=50", collectorToken);
        assertThat(page.path("__status").asInt()).isEqualTo(200);
        assertThat(page.path("totalElements").asInt()).isEqualTo(1);
        assertThat(page.path("content").get(0).path("code").asText()).isEqualTo(chennaiA);
        assertThat(page.path("content").get(0).path("city").asText()).isEqualTo("Chennai");
    }

    @Test
    @DisplayName("filtering by material returns only requests for that material")
    void materialFilterNarrowsToThatMaterial() {
        JsonNode page = get("/api/collector/pickups?material=" + categoryBCode + "&size=50", collectorToken);
        assertThat(page.path("__status").asInt()).isEqualTo(200);
        assertThat(page.path("totalElements").asInt()).isEqualTo(1);
        assertThat(page.path("content").get(0).path("code").asText()).isEqualTo(hyderabadB);
        assertThat(page.path("content").get(0).path("category").path("code").asText()).isEqualTo(categoryBCode);
    }

    @Test
    @DisplayName("city and material filters combine, and an empty result is an empty page, not everything")
    void filtersCombineAndNarrowRatherThanWiden() {
        assertThat(get("/api/collector/pickups?city=Hyderabad&material=" + categoryACode + "&size=50", collectorToken)
                .path("totalElements").asInt()).isEqualTo(1);

        JsonNode noMatch = get("/api/collector/pickups?city=Hyderabad&material=" + categoryBCode
                + "&size=50&page=0", collectorToken);
        // The Chennai request is material A in another city, so only hyderabadB can match here.
        assertThat(noMatch.path("totalElements").asInt()).isEqualTo(1);

        JsonNode impossible = get("/api/collector/pickups?city=Nowhere", collectorToken);
        assertThat(impossible.path("totalElements").asInt()).isZero();
        assertThat(impossible.path("content").size()).isZero();
    }

    // ------------------------------------------------------------------ radius

    @Test
    @DisplayName("a radius filter drops requests beyond it and orders the rest nearest-first")
    void radiusFilterExcludesDistantRequests() {
        JsonNode page = get("/api/collector/pickups?lat=" + CALLER_LAT + "&lng=" + CALLER_LNG
                + "&maxDistanceKm=25&size=50", collectorToken);
        assertThat(page.path("__status").asInt()).isEqualTo(200);

        JsonNode content = page.path("content");
        assertThat(page.path("totalElements").asInt()).isEqualTo(2);
        assertThat(nodeList(content)).extracting(node -> node.path("code").asText())
                .containsExactlyInAnyOrder(hyderabadA, hyderabadB)
                .doesNotContain(chennaiA);

        // totalElements reflects the filtered set, not just the first page of unfiltered matches.
        List<Integer> distances = nodeList(content).stream()
                .map(node -> node.path("approximateDistanceKm").asInt())
                .toList();
        assertThat(distances).isSorted();
        assertThat(distances).allSatisfy(distance -> assertThat(distance).isLessThanOrEqualTo(25));
    }

    @Test
    @DisplayName("a radius filter without a position is rejected rather than silently ignored")
    void radiusFilterNeedsAPosition() {
        JsonNode response = get("/api/collector/pickups?maxDistanceKm=25", collectorToken);
        assertThat(response.path("__status").asInt()).isEqualTo(400);
        assertThat(response.path("message").asText()).contains("lat and lng");
    }

    @Test
    @DisplayName("a non-positive radius is rejected")
    void radiusMustBePositive() {
        JsonNode response = get("/api/collector/pickups?lat=" + CALLER_LAT + "&lng=" + CALLER_LNG
                + "&maxDistanceKm=0", collectorToken);
        assertThat(response.path("__status").asInt()).isEqualTo(400);
    }

    // ------------------------------------------------------------------ privacy is unchanged

    @Test
    @DisplayName("the pool still carries no requester details, filtered or not")
    void poolNeverLeaksRequesterDetails() {
        for (String uri : List.of(
                "/api/collector/pickups?size=50",
                "/api/collector/pickups?city=Hyderabad&size=50",
                "/api/collector/pickups?lat=" + CALLER_LAT + "&lng=" + CALLER_LNG + "&maxDistanceKm=25&size=50")) {

            JsonNode content = get(uri, collectorToken).path("content");
            assertThat(content.size()).as("results for %s", uri).isPositive();
            content.forEach(summary -> assertThat(summary.fieldNames())
                    .toIterable()
                    .as("fields exposed for %s via %s", summary.path("code").asText(), uri)
                    .containsExactlyInAnyOrder("code", "status", "category", "estimatedQuantityKg",
                            "city", "pickupDate", "timeSlot", "createdAt", "approximateDistanceKm"));
        }
    }

    @Test
    @DisplayName("the resident's own record still exists — filtering hides nothing from the owner")
    void filteringDoesNotMutateTheUnderlyingRecords() {
        JsonNode pickup = get("/api/pickups/" + hyderabadA, residentToken);
        assertThat(pickup.path("__status").asInt()).isEqualTo(200);
        assertThat(pickup.path("address").asText()).isEqualTo("42 Private Lane, Flat 7B");
        assertThat(pickup.path("notes").asText()).isEqualTo(RESIDENT_NOTES);
    }

    // ------------------------------------------------------------------ authorization

    @Test
    @DisplayName("an unverified applicant cannot read the pool or its filter options")
    void unverifiedCollectorIsRefused() {
        assertThat(get("/api/collector/pickups?size=50", pendingCollectorToken).path("__status").asInt())
                .isEqualTo(403);
        assertThat(get("/api/collector/pickups/filters", pendingCollectorToken).path("__status").asInt())
                .isEqualTo(403);
    }

    @Test
    @DisplayName("a resident cannot read the collector pool")
    void residentIsRefused() {
        assertThat(get("/api/collector/pickups?size=50", residentToken).path("__status").asInt()).isEqualTo(403);
        assertThat(get("/api/collector/pickups/filters", residentToken).path("__status").asInt()).isEqualTo(403);
    }

    @Test
    @DisplayName("the pool only ever contains unassigned requests")
    void acceptedWorkLeavesThePool() {
        assertThat(get("/api/collector/pickups?size=50", collectorToken).path("totalElements").asInt())
                .isEqualTo(3);

        assertThat(patch("/api/collector/pickups/" + hyderabadA + "/accept", collectorToken, null)
                .path("__status").asInt()).isEqualTo(200);

        JsonNode pool = get("/api/collector/pickups?size=50", collectorToken);
        assertThat(pool.path("totalElements").asInt()).isEqualTo(2);
        assertThat(nodeList(pool.path("content"))).extracting(node -> node.path("code").asText())
                .doesNotContain(hyderabadA);

        JsonNode filters = get("/api/collector/pickups/filters", collectorToken);
        assertThat(textList(filters.path("cities"))).contains("Hyderabad"); // hyderabadB remains open

        // A filter answers for what is genuinely still waiting: category A has one open request
        // left (the Chennai one) rather than the two it had before the accept.
        JsonNode byCategoryA = get("/api/collector/pickups?material=" + categoryACode + "&size=50", collectorToken);
        assertThat(byCategoryA.path("totalElements").asInt()).isEqualTo(1);
        assertThat(nodeList(byCategoryA.path("content"))).extracting(node -> node.path("code").asText())
                .containsExactly(chennaiA);

        // Hand it back so the fixtures are identical for every other test in this class, which
        // makes the class independent of JUnit's method ordering.
        assertThat(patch("/api/collector/pickups/" + hyderabadA + "/release", collectorToken, null)
                .path("__status").asInt()).isEqualTo(200);
        assertThat(get("/api/collector/pickups?size=50", collectorToken).path("totalElements").asInt())
                .isEqualTo(3);
    }

    // ------------------------------------------------------------------ helpers

    private List<String> textList(JsonNode arrayNode) {
        assertThat(arrayNode.isArray()).as("expected a JSON array").isTrue();
        return streamOf(arrayNode).map(JsonNode::asText).toList();
    }

    private List<JsonNode> nodeList(JsonNode arrayNode) {
        assertThat(arrayNode.isArray()).as("expected a JSON array").isTrue();
        return streamOf(arrayNode).toList();
    }

    private java.util.stream.Stream<JsonNode> streamOf(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return java.util.stream.Stream.empty();
        }
        return java.util.stream.StreamSupport.stream(arrayNode.spliterator(), false);
    }

    private Map<String, Object> application(String organisation) {
        return Map.of(
                "organizationName", organisation,
                "contactPerson", "Pool Contact",
                "phone", "9888888888",
                "address", "9 Industrial Estate",
                "city", "Hyderabad",
                "pincode", "500081",
                "operatingHours", "Mon-Sat 9am-6pm",
                "registrationNumber", "TS-POOL-0001",
                "materialCodes", List.of("PLASTIC", "CARDBOARD"));
    }

    /** Registers, applies, gets admin-verified, then re-logs in — the role is baked into the JWT. */
    private String verifiedCollector(String email, String organisation) {
        register(email, organisation + " Contact");
        String token = login(email, PASSWORD);
        String partnerId = post("/api/collectors/apply", token, application(organisation)).path("id").asText();
        assertThat(patch("/api/admin/collectors/" + partnerId + "/verify", adminToken, null)
                .path("__status").asInt()).isEqualTo(200);
        return login(email, PASSWORD);
    }

    private String createPickup(String categoryId, String city, String pincode, String lat, String lng) {
        JsonNode created = postMultipart("/api/pickups", residentToken, Map.of(
                "categoryId", categoryId,
                "estimatedQuantityKg", "2.50",
                "address", "42 Private Lane, Flat 7B",
                "city", city,
                "pincode", pincode,
                "latitude", lat,
                "longitude", lng,
                "pickupDate", LocalDate.now().plusDays(1).toString(),
                "timeSlot", "MORNING",
                "notes", RESIDENT_NOTES));
        assertThat(created.path("__status").asInt()).as("create pickup in %s", city).isEqualTo(201);
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
}
