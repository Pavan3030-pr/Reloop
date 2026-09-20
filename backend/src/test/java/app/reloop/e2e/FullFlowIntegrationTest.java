package app.reloop.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test of the complete ReLoop flow against a real Postgres schema
 * (reloop_test) and the real HTTP stack:
 * register → login → scan → AI (503 fallback without a Gemini key) → collection
 * point lookup → pickup request → collector accept/schedule → actual weight
 * collection → processing → recovered → notifications → history → impact.
 * Authorization (USER/COLLECTOR/ADMIN) and invalid state transitions are verified.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
class FullFlowIntegrationTest {

    private static final String PNG_1X1 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJ"
            + "AAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private TestRestTemplate http;
    @Autowired
    private JdbcTemplate jdbc;

    private String userEmail;
    private String userPassword = "User#Passw0rd1";
    private String userToken;
    private String collectorEmail;
    private String collectorToken;
    private String adminToken;
    private String partnerId;
    private String plasticCategoryId;
    private String cardboardCategoryId;
    private String scanId;
    private String pickupCode;
    private String cancelPickupCode;
    private String collectionPointId;
    private byte[] pngBytes;

    @DynamicPropertySource
    static void bootstrapAdmin(DynamicPropertyRegistry registry) {
        registry.add("reloop.admin.email", () -> "e2e-admin@reloop.test");
        registry.add("reloop.admin.password", () -> "Admin#Passw0rd1");
    }

    @BeforeAll
    void resetTransactionalData() {
        pngBytes = Base64.getDecoder().decode(PNG_1X1);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        userEmail = "resident-" + suffix + "@reloop.test";
        collectorEmail = "collector-" + suffix + "@reloop.test";

        // Start from a clean slate for flow data (categories and the bootstrap admin stay).
        jdbc.execute("TRUNCATE TABLE collected_waste, pickup_requests, notifications, waste_scans, "
                + "collection_points, collection_point_materials, collection_partners, "
                + "collection_partner_materials, refresh_tokens, password_reset_tokens RESTART IDENTITY CASCADE");
    }

    // ------------------------------------------------------------------ helpers

    private HttpHeaders auth(String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }

    private JsonNode exchange(HttpMethod method, String uri, String token, HttpEntity<?> entity) {
        ResponseEntity<String> response = http.exchange(uri, method, entity, String.class);
        return toNode(response);
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

    private JsonNode get(String uri, String token) {
        return exchange(HttpMethod.GET, uri, token, new HttpEntity<>(auth(token)));
    }

    private JsonNode post(String uri, String token, Object body) {
        HttpHeaders headers = auth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return exchange(HttpMethod.POST, uri, token, new HttpEntity<>(body, headers));
    }

    private JsonNode patch(String uri, String token, Object body) {
        HttpHeaders headers = auth(token);
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return exchange(HttpMethod.PATCH, uri, token, new HttpEntity<>(body, headers));
    }

    private JsonNode put(String uri, String token, Object body) {
        HttpHeaders headers = auth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return exchange(HttpMethod.PUT, uri, token, new HttpEntity<>(body, headers));
    }

    @SuppressWarnings("unchecked")
    private JsonNode postMultipart(String uri, String token, Map<String, Object> fields,
                                   String fileField, byte[] bytes, String filename, String mime) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        fields.forEach((key, value) -> body.add(key, value == null ? "" : value.toString()));
        if (bytes != null) {
            ByteArrayResource resource = new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };
            HttpHeaders fileHeaders = new HttpHeaders();
            fileHeaders.setContentType(MediaType.parseMediaType(mime));
            body.add(fileField, new HttpEntity<>(resource, fileHeaders));
        }
        HttpHeaders headers = auth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return exchange(HttpMethod.POST, uri, token, new HttpEntity<>(body, headers));
    }

    private String register(String email, String password, String fullName) {
        return post("/api/auth/register", null, Map.of(
                "email", email, "password", password, "fullName", fullName, "phone", "9999999999"))
                .path("accessToken").asText();
    }

    // ------------------------------------------------------------------ 1. auth

    @Test
    @Order(1)
    void step01_registerLoginAndDuplicateGuard() {
        JsonNode registered = post("/api/auth/register", null, Map.of(
                "email", userEmail, "password", userPassword, "fullName", "Test Resident"));
        assertThat(registered.path("__status").asInt()).isEqualTo(201);
        assertThat(registered.path("accessToken").asText()).isNotBlank();
        assertThat(registered.path("refreshToken").asText()).isNotBlank();
        assertThat(registered.path("user").path("role").asText()).isEqualTo("USER");
        userToken = registered.path("accessToken").asText();

        JsonNode duplicate = post("/api/auth/register", null, Map.of(
                "email", userEmail, "password", userPassword, "fullName", "Test Resident"));
        assertThat(duplicate.path("__status").asInt()).isEqualTo(409);

        JsonNode login = post("/api/auth/login", null,
                Map.of("email", userEmail, "password", userPassword));
        assertThat(login.path("__status").asInt()).isEqualTo(200);
        userToken = login.path("accessToken").asText();

        JsonNode me = get("/api/auth/me", userToken);
        assertThat(me.path("__status").asInt()).isEqualTo(200);
        assertThat(me.path("email").asText()).isEqualTo(userEmail);

        JsonNode badLogin = post("/api/auth/login", null,
                Map.of("email", userEmail, "password", "Wrong#Passw0rd"));
        assertThat(badLogin.path("__status").asInt()).isEqualTo(403);

        JsonNode refreshed = post("/api/auth/refresh", null,
                Map.of("refreshToken", login.path("refreshToken").asText()));
        assertThat(refreshed.path("__status").asInt()).isEqualTo(200);
        assertThat(refreshed.path("accessToken").asText()).isNotBlank();
    }

    @Test
    @Order(2)
    void step02_categoriesArePublicWithSeedData() {
        JsonNode categories = get("/api/waste/categories", null);
        assertThat(categories.path("__status").asInt()).isEqualTo(200);
        // The endpoint returns a plain array (wrapped as __array); the fallback keeps the test honest if it ever becomes a Page.
        JsonNode items = categories.has("__array") ? categories.path("__array")
                : categories.has("content") && categories.path("content").isArray()
                ? categories.path("content") : categories;
        assertThat(items.isArray()).isTrue();
        assertThat(items.size()).isGreaterThanOrEqualTo(10);
        for (JsonNode category : items) {
            if ("PLASTIC".equals(category.path("code").asText())) {
                plasticCategoryId = category.path("id").asText();
            }
            if ("CARDBOARD".equals(category.path("code").asText())) {
                cardboardCategoryId = category.path("id").asText();
            }
        }
        assertThat(plasticCategoryId).isNotBlank();
        assertThat(cardboardCategoryId).isNotBlank();
    }

    @Test
    @Order(3)
    void step03_manualScanSaveListAndGet() {
        JsonNode saved = postMultipart("/api/waste/scans", userToken, Map.of(
                "categoryId", plasticCategoryId,
                "detectedItem", "Plastic water bottle",
                "recyclable", "true",
                "hazardous", "false",
                "disposalInstruction", "Rinse and dry before the plastic stream."),
                "image", pngBytes, "bottle.png", "image/png");
        assertThat(saved.path("__status").asInt()).isEqualTo(201);
        assertThat(saved.path("source").asText()).isEqualTo("MANUAL");
        assertThat(saved.path("category").path("code").asText()).isEqualTo("PLASTIC");
        assertThat(saved.path("imageUrl").asText()).contains("/uploads/");
        assertThat(saved.path("confidence").isNull()).isTrue();
        scanId = saved.path("id").asText();

        JsonNode listed = get("/api/waste/scans?page=0&size=20", userToken);
        assertThat(listed.path("__status").asInt()).isEqualTo(200);
        assertThat(listed.path("content").size()).isGreaterThanOrEqualTo(1);

        JsonNode single = get("/api/waste/scans/" + scanId, userToken);
        assertThat(single.path("__status").asInt()).isEqualTo(200);
        assertThat(single.path("detectedItem").asText()).isEqualTo("Plastic water bottle");
    }

    @Test
    @Order(4)
    void step04_analyzeWithoutGeminiKeyReturns503NotFakeData() {
        JsonNode analyzed = postMultipart("/api/waste/analyze", userToken, Map.of(),
                "image", pngBytes, "item.png", "image/png");
        assertThat(analyzed.path("__status").asInt()).isEqualTo(503);
        assertThat(analyzed.path("message").asText()).containsIgnoringCase("AI analysis");
    }

    @Test
    @Order(5)
    void step05_collectorApplicationPendingAndDashboardForbidden() {
        collectorToken = register(collectorEmail, "Coll#Passw0rd1", "Green Cycles");
        JsonNode applied = post("/api/collectors/apply", collectorToken, Map.of(
                "organizationName", "Green Cycles Pvt Ltd",
                "contactPerson", "Green Cycles",
                "phone", "8888888888",
                "address", "12 Industrial Estate",
                "city", "Hyderabad",
                "pincode", "500081",
                "operatingHours", "Mon-Sat 9am-6pm",
                "registrationNumber", "TS-RCB-2024-1122",
                "materialCodes", java.util.List.of("PLASTIC", "CARDBOARD")));
        assertThat(applied.path("__status").asInt()).isEqualTo(201);
        assertThat(applied.path("status").asText()).isEqualTo("PENDING");
        partnerId = applied.path("id").asText();

        JsonNode mine = get("/api/collectors/me", collectorToken);
        assertThat(mine.path("__status").asInt()).isEqualTo(200);
        assertThat(mine.path("organizationName").asText()).isEqualTo("Green Cycles Pvt Ltd");

        JsonNode dashboard = get("/api/collector/dashboard", collectorToken);
        assertThat(dashboard.path("__status").asInt()).isEqualTo(403);
    }

    @Test
    @Order(6)
    void step06_adminVerifiesCollectorWhoBecomesCollector() {
        JsonNode adminLogin = post("/api/auth/login", null,
                Map.of("email", "e2e-admin@reloop.test", "password", "Admin#Passw0rd1"));
        assertThat(adminLogin.path("__status").asInt()).isEqualTo(200);
        assertThat(adminLogin.path("user").path("role").asText()).isEqualTo("ADMIN");
        adminToken = adminLogin.path("accessToken").asText();

        JsonNode pending = get("/api/admin/collectors?status=PENDING", adminToken);
        assertThat(pending.path("__status").asInt()).isEqualTo(200);
        Optional<JsonNode> partner = streamOf(pending.path("content"))
                .filter(node -> node.path("id").asText().equals(partnerId)).findFirst();
        assertThat(partner).isPresent();

        JsonNode verified = patch("/api/admin/collectors/" + partnerId + "/verify", adminToken, null);
        assertThat(verified.path("__status").asInt()).isEqualTo(200);
        assertThat(verified.path("status").asText()).isEqualTo("VERIFIED");

        JsonNode collectorMe = get("/api/auth/me", collectorToken);
        assertThat(collectorMe.path("role").asText()).isEqualTo("COLLECTOR");

        JsonNode dashboard = get("/api/collector/dashboard", collectorToken);
        assertThat(dashboard.path("__status").asInt()).isEqualTo(200);
        assertThat(dashboard.path("availableRequests").asLong()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @Order(7)
    void step07_adminCreatesCollectionPointFoundByPublicSearch() {
        JsonNode created = post("/api/admin/collection-points", adminToken, Map.of(
                "name", "Kukatpally Recycling Hub",
                "address", "Plot 44, KPHB Phase 3",
                "city", "Hyderabad",
                "pincode", "500072",
                "latitude", 17.4948,
                "longitude", 78.3996,
                "operatingHours", "Mon-Sat 9am-7pm",
                "contactPhone", "7777777777",
                "materialCodes", java.util.List.of("PLASTIC", "CARDBOARD", "METAL")));
        assertThat(created.path("__status").asInt()).isEqualTo(201);
        collectionPointId = created.path("id").asText();

        // Authenticated search near the point (roughly 2 km away).
        JsonNode found = get("/api/collection-points?city=Hyderabad&lat=17.48&lng=78.41&radiusKm=5", userToken);
        assertThat(found.path("__status").asInt()).isEqualTo(200);
        Optional<JsonNode> match = streamOf(found.path("__array")).filter(node ->
                node.path("id").asText().equals(collectionPointId)).findFirst();
        assertThat(match).isPresent();
        assertThat(match.get().path("distanceKm").asDouble()).isLessThanOrEqualTo(5.0);
        assertThat(match.get().path("materials").size()).isEqualTo(3);

        JsonNode byMaterial = get("/api/collection-points?material=GLASS", userToken);
        assertThat(streamOf(byMaterial.path("__array")).noneMatch(node ->
                node.path("id").asText().equals(collectionPointId))).isTrue();

        JsonNode single = get("/api/collection-points/" + collectionPointId, userToken);
        assertThat(single.path("__status").asInt()).isEqualTo(200);
        assertThat(single.path("name").asText()).isEqualTo("Kukatpally Recycling Hub");
    }

    @Test
    @Order(8)
    void step08_userCreatesPickupWithValidation() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        JsonNode created = postMultipart("/api/pickups", userToken, Map.of(
                "categoryId", plasticCategoryId,
                "estimatedQuantityKg", "2.5",
                "address", "Flat 302, Lake View Residency",
                "city", "Hyderabad",
                "pincode", "500035",
                "latitude", "17.4239",
                "longitude", "78.3428",
                "pickupDate", tomorrow.toString(),
                "timeSlot", "MORNING",
                "notes", "Bags of crushed bottles near the gate."),
                "photo", pngBytes, "pile.png", "image/png");
        assertThat(created.path("__status").asInt()).isEqualTo(201);
        assertThat(created.path("code").asText()).startsWith("RL-");
        assertThat(created.path("status").asText()).isEqualTo("REQUESTED");
        assertThat(created.path("estimatedQuantityKg").asDouble()).isEqualTo(2.5);
        assertThat(created.path("photoUrl").asText()).contains("/uploads/");
        assertThat(created.path("requesterName").asText()).isEqualTo("Test Resident");
        pickupCode = created.path("code").asText();

        JsonNode pastDate = postMultipart("/api/pickups", userToken, Map.of(
                "categoryId", plasticCategoryId,
                "estimatedQuantityKg", "1",
                "address", "somewhere",
                "city", "Hyderabad",
                "pickupDate", LocalDate.now().minusDays(1).toString(),
                "timeSlot", "MORNING"),
                null, null, null, null);
        assertThat(pastDate.path("__status").asInt()).isEqualTo(400);

        JsonNode zeroQty = postMultipart("/api/pickups", userToken, Map.of(
                "categoryId", plasticCategoryId,
                "estimatedQuantityKg", "0",
                "address", "somewhere",
                "city", "Hyderabad",
                "pickupDate", tomorrow.toString(),
                "timeSlot", "MORNING"),
                null, null, null, null);
        assertThat(zeroQty.path("__status").asInt()).isEqualTo(400);

        JsonNode second = postMultipart("/api/pickups", userToken, Map.of(
                "categoryId", cardboardCategoryId,
                "estimatedQuantityKg", "1.0",
                "address", "Flat 302, Lake View Residency",
                "city", "Hyderabad",
                "pickupDate", tomorrow.toString(),
                "timeSlot", "AFTERNOON"),
                null, null, null, null);
        assertThat(second.path("__status").asInt()).isEqualTo(201);
        cancelPickupCode = second.path("code").asText();

        JsonNode listed = get("/api/pickups", userToken);
        assertThat(streamOf(listed.path("content")).anyMatch(node ->
                node.path("code").asText().equals(pickupCode))).isTrue();
    }

    @Test
    @Order(9)
    void step09_userCancelsUnassignedPickupOnlyOnce() {
        JsonNode cancelled = patch("/api/pickups/" + cancelPickupCode + "/cancel", userToken, null);
        assertThat(cancelled.path("__status").asInt()).isEqualTo(200);
        assertThat(cancelled.path("status").asText()).isEqualTo("CANCELLED");

        JsonNode again = patch("/api/pickups/" + cancelPickupCode + "/cancel", userToken, null);
        assertThat(again.path("__status").asInt()).isEqualTo(409);
    }

    @Test
    @Order(10)
    void step10_collectorLifecycleWithActualWeight() {
        JsonNode available = get("/api/collector/pickups?scope=available", collectorToken);
        assertThat(available.path("__status").asInt()).isEqualTo(200);
        assertThat(streamOf(available.path("content")).anyMatch(node ->
                node.path("code").asText().equals(pickupCode))).isTrue();

        JsonNode accepted = patch("/api/collector/pickups/" + pickupCode + "/accept", collectorToken, null);
        assertThat(accepted.path("__status").asInt()).isEqualTo(200);
        assertThat(accepted.path("status").asText()).isEqualTo("ACCEPTED");
        assertThat(accepted.path("collectorOrganization").asText()).isEqualTo("Green Cycles Pvt Ltd");

        JsonNode tooEarly = patch("/api/collector/pickups/" + pickupCode + "/schedule", collectorToken,
                Map.of("scheduledAt", Instant.now().minus(Duration.ofHours(1)).toString()));
        assertThat(tooEarly.path("__status").asInt()).isEqualTo(400);

        JsonNode scheduled = patch("/api/collector/pickups/" + pickupCode + "/schedule", collectorToken,
                Map.of("scheduledAt", Instant.now().plus(Duration.ofHours(2)).toString()));
        assertThat(scheduled.path("__status").asInt()).isEqualTo(200);
        assertThat(scheduled.path("status").asText()).isEqualTo("SCHEDULED");

        JsonNode zeroWeight = patch("/api/collector/pickups/" + pickupCode + "/collect", collectorToken,
                Map.of("actualQuantityKg", "0"));
        assertThat(zeroWeight.path("__status").asInt()).isEqualTo(400);

        JsonNode collected = patch("/api/collector/pickups/" + pickupCode + "/collect", collectorToken,
                Map.of("actualQuantityKg", "2.4", "notes", "Two bags weighed on site"));
        assertThat(collected.path("__status").asInt()).isEqualTo(200);
        assertThat(collected.path("status").asText()).isEqualTo("PICKED_UP");
        assertThat(collected.path("actualQuantityKg").asDouble()).isEqualTo(2.4);

        JsonNode processing = patch("/api/collector/pickups/" + pickupCode + "/status", collectorToken,
                Map.of("status", "PROCESSING"));
        assertThat(processing.path("__status").asInt()).isEqualTo(200);
        assertThat(processing.path("status").asText()).isEqualTo("PROCESSING");

        JsonNode invalidTransition = patch("/api/collector/pickups/" + pickupCode + "/status", collectorToken,
                Map.of("status", "ACCEPTED"));
        assertThat(invalidTransition.path("__status").asInt()).isEqualTo(400);

        JsonNode recovered = patch("/api/collector/pickups/" + pickupCode + "/status", collectorToken,
                Map.of("status", "RECOVERED"));
        assertThat(recovered.path("__status").asInt()).isEqualTo(200);
        assertThat(recovered.path("status").asText()).isEqualTo("RECOVERED");

        JsonNode collectAgain = patch("/api/collector/pickups/" + pickupCode + "/collect", collectorToken,
                Map.of("actualQuantityKg", "2.4"));
        assertThat(collectAgain.path("__status").asInt()).isEqualTo(409);

        JsonNode userCancelAfterCollection = patch("/api/pickups/" + pickupCode + "/cancel", userToken, null);
        assertThat(userCancelAfterCollection.path("__status").asInt()).isEqualTo(409);
    }

    @Test
    @Order(11)
    void step11_collectorDashboardAndMine() {
        JsonNode dashboard = get("/api/collector/dashboard", collectorToken);
        assertThat(dashboard.path("__status").asInt()).isEqualTo(200);
        assertThat(dashboard.path("completedJobs").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(dashboard.path("totalCollections").asLong()).isEqualTo(1);
        assertThat(dashboard.path("totalKgCollected").asDouble()).isEqualTo(2.4);

        JsonNode mine = get("/api/collector/pickups?scope=mine", collectorToken);
        assertThat(streamOf(mine.path("content")).anyMatch(node ->
                node.path("code").asText().equals(pickupCode))).isTrue();
    }

    @Test
    @Order(12)
    void step12_notificationsGeneratedAndMarkedRead() {
        JsonNode unread = get("/api/notifications/unread-count", userToken);
        assertThat(unread.path("__status").asInt()).isEqualTo(200);
        long before = unread.path("count").asLong();
        assertThat(before).isGreaterThanOrEqualTo(6); // welcome, accepted, scheduled, collected, processing, recovered

        JsonNode list = get("/api/notifications", userToken);
        var types = streamOf(list.path("content")).map(node -> node.path("type").asText()).toList();
        assertThat(types).contains("PICKUP_ACCEPTED", "PICKUP_SCHEDULED", "PICKUP_COLLECTED",
                "PICKUP_PROCESSING", "PICKUP_RECOVERED");

        String firstUnreadId = streamOf(list.path("content"))
                .filter(node -> !node.path("read").asBoolean())
                .map(node -> node.path("id").asText())
                .findFirst().orElseThrow();
        JsonNode marked = patch("/api/notifications/" + firstUnreadId + "/read", userToken, null);
        assertThat(marked.path("__status").asInt()).isEqualTo(204);

        JsonNode afterOne = get("/api/notifications/unread-count", userToken);
        assertThat(afterOne.path("count").asLong()).isEqualTo(before - 1);

        JsonNode markAll = patch("/api/notifications/read-all", userToken, null);
        assertThat(markAll.path("__status").asInt()).isEqualTo(204);
        JsonNode afterAll = get("/api/notifications/unread-count", userToken);
        assertThat(afterAll.path("count").asLong()).isZero();
    }

    @Test
    @Order(13)
    void step13_historyFeedsFromActualWeightWithFilters() {
        JsonNode history = get("/api/history", userToken);
        assertThat(history.path("__status").asInt()).isEqualTo(200);
        assertThat(history.path("totalKg").asDouble()).isEqualTo(2.4);
        assertThat(history.path("byCategory").size()).isEqualTo(1);
        assertThat(history.path("byCategory").get(0).path("code").asText()).isEqualTo("PLASTIC");

        JsonNode entry = streamOf(history.path("entries").path("content"))
                .filter(node -> node.path("pickupCode").asText().equals(pickupCode))
                .findFirst().orElseThrow();
        assertThat(entry.path("quantityKg").asDouble()).isEqualTo(2.4);
        assertThat(entry.path("pickupStatus").asText()).isEqualTo("RECOVERED");
        assertThat(entry.path("categoryCode").asText()).isEqualTo("PLASTIC");
        assertThat(entry.path("collectorOrganization").asText()).isEqualTo("Green Cycles Pvt Ltd");
        assertThat(entry.path("notes").asText()).isEqualTo("Two bags weighed on site");

        assertThat(streamOf(get("/api/history?material=GLASS", userToken).path("entries").path("content")))
                .isEmpty();
        assertThat(streamOf(get("/api/history?status=CANCELLED", userToken).path("entries").path("content")))
                .isEmpty();
        assertThat(streamOf(get("/api/history?status=RECOVERED", userToken).path("entries").path("content"))
                .anyMatch(node -> node.path("pickupCode").asText().equals(pickupCode))).isTrue();
        assertThat(streamOf(get("/api/history?from=" + LocalDate.now(), userToken).path("entries").path("content"))
                .anyMatch(node -> node.path("pickupCode").asText().equals(pickupCode))).isTrue();
        assertThat(get("/api/history?material=NOT_A_MATERIAL", userToken).path("__status").asInt()).isEqualTo(400);
    }

    @Test
    @Order(14)
    void step14_impactComputedFromActualCollectedWeight() {
        JsonNode impact = get("/api/impact", userToken);
        assertThat(impact.path("__status").asInt()).isEqualTo(200);
        assertThat(impact.path("totalCollectedKg").asDouble()).isEqualTo(2.4);
        assertThat(impact.path("completedPickups").asLong()).isEqualTo(1);
        assertThat(impact.path("estimatesAreApproximations").asBoolean()).isTrue();
        assertThat(impact.path("methodology").asText()).contains("docs/IMPACT.md");

        JsonNode plastic = streamOf(impact.path("byCategory"))
                .filter(node -> node.path("code").asText().equals("PLASTIC"))
                .findFirst().orElseThrow();
        assertThat(plastic.path("kg").asDouble()).isEqualTo(2.4);
        // 2.4 kg plastic × 1.5 kg CO2e/kg (conservative literature coefficient)
        assertThat(plastic.path("estimatedCo2eKgSaved").asDouble())
                .isEqualTo(new BigDecimal("2.4").multiply(BigDecimal.valueOf(1.5))
                        .setScale(2, RoundingMode.HALF_UP).doubleValue());
    }

    @Test
    @Order(15)
    void step15_authorizationAndOwnershipEnforced() {
        JsonNode anonymous = get("/api/pickups", null);
        assertThat(anonymous.path("__status").asInt()).isEqualTo(401);

        JsonNode userAsAdmin = get("/api/admin/users", userToken);
        assertThat(userAsAdmin.path("__status").asInt()).isEqualTo(403);

        JsonNode userAsCollector = get("/api/collector/dashboard", userToken);
        assertThat(userAsCollector.path("__status").asInt()).isEqualTo(403);

        JsonNode collectorAsAdmin = get("/api/admin/analytics", collectorToken);
        assertThat(collectorAsAdmin.path("__status").asInt()).isEqualTo(403);

        String outsiderToken = register("outsider-" + UUID.randomUUID().toString().substring(0, 8)
                + "@reloop.test", "Out#Passw0rd1", "Outsider");
        JsonNode outsiderPickup = get("/api/pickups/" + pickupCode, outsiderToken);
        assertThat(outsiderPickup.path("__status").asInt()).isEqualTo(403);

        JsonNode outsiderScan = get("/api/waste/scans/" + scanId, outsiderToken);
        assertThat(outsiderScan.path("__status").asInt()).isEqualTo(403);

        JsonNode collectorViewsPickup = get("/api/pickups/" + pickupCode, collectorToken);
        assertThat(collectorViewsPickup.path("__status").asInt()).isEqualTo(200);

        JsonNode adminViewsPickup = get("/api/pickups/" + pickupCode, adminToken);
        assertThat(adminViewsPickup.path("__status").asInt()).isEqualTo(200);
    }

    @Test
    @Order(16)
    void step16_adminDirectoryAndAnalytics() {
        // No status filter exercises the nullable-parameter path in the catalog queries.
        JsonNode collectors = get("/api/admin/collectors", adminToken);
        assertThat(collectors.path("__status").asInt()).isEqualTo(200);
        assertThat(streamOf(collectors.path("content")).anyMatch(node ->
                node.path("id").asText().equals(partnerId))).isTrue();

        JsonNode users = get("/api/admin/users", adminToken);
        assertThat(users.path("__status").asInt()).isEqualTo(200);
        assertThat(users.path("totalElements").asLong()).isGreaterThanOrEqualTo(2);

        // The q filter must actually filter, not silently ignore the query.
        JsonNode filtered = get("/api/admin/users?q=" + collectorEmail, adminToken);
        assertThat(filtered.path("totalElements").asLong()).isEqualTo(1);
        assertThat(streamOf(filtered.path("content")).findFirst().orElseThrow()
                .path("email").asText()).isEqualTo(collectorEmail);

        JsonNode pickups = get("/api/admin/pickups", adminToken);
        assertThat(pickups.path("__status").asInt()).isEqualTo(200);

        JsonNode analytics = get("/api/admin/analytics", adminToken);
        assertThat(analytics.path("__status").asInt()).isEqualTo(200);
        assertThat(analytics.path("verifiedCollectors").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(analytics.path("totalCollectedKg").asDouble()).isEqualTo(2.4);
        assertThat(analytics.path("pickupsByStatus").path("RECOVERED").asLong()).isEqualTo(1);
    }

    @Test
    @Order(17)
    void step17_profileReadAndUpdate() {
        JsonNode profile = get("/api/profile", userToken);
        assertThat(profile.path("__status").asInt()).isEqualTo(200);
        assertThat(profile.path("fullName").asText()).isEqualTo("Test Resident");

        JsonNode updated = put("/api/profile", userToken, Map.of(
                "fullName", "Test Resident Updated",
                "phone", "9000000000",
                "city", "Hyderabad",
                "addressLine", "Flat 302, Lake View Residency"));
        assertThat(updated.path("__status").asInt()).isEqualTo(200);
        assertThat(updated.path("fullName").asText()).isEqualTo("Test Resident Updated");

        JsonNode reread = get("/api/profile", userToken);
        assertThat(reread.path("phone").asText()).isEqualTo("9000000000");
    }

    @Test
    @Order(18)
    void step18_passwordResetWithDevToken() {
        JsonNode forgot = post("/api/auth/forgot-password", null, Map.of("email", userEmail));
        assertThat(forgot.path("__status").asInt()).isEqualTo(200);
        String resetToken = forgot.path("devResetToken").asText();
        assertThat(resetToken).isNotBlank(); // dev mode enabled in the test profile

        JsonNode reset = post("/api/auth/reset-password", null,
                Map.of("token", resetToken, "newPassword", "New#Passw0rd9"));
        assertThat(reset.path("__status").asInt()).isEqualTo(204);

        JsonNode oldPassword = post("/api/auth/login", null,
                Map.of("email", userEmail, "password", userPassword));
        assertThat(oldPassword.path("__status").asInt()).isEqualTo(403);

        JsonNode newPassword = post("/api/auth/login", null,
                Map.of("email", userEmail, "password", "New#Passw0rd9"));
        assertThat(newPassword.path("__status").asInt()).isEqualTo(200);
    }

    // ------------------------------------------------------------------ utils

    private static java.util.stream.Stream<JsonNode> streamOf(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return java.util.stream.Stream.empty();
        }
        return java.util.stream.StreamSupport.stream(arrayNode.spliterator(), false);
    }
}
