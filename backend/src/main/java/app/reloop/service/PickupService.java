package app.reloop.service;

import app.reloop.dto.pickup.CollectPickupRequest;
import app.reloop.dto.pickup.CollectorDashboardDto;
import app.reloop.dto.pickup.CreatePickupRequest;
import app.reloop.dto.pickup.PickupDto;
import app.reloop.dto.pickup.OpenPoolFiltersDto;
import app.reloop.dto.pickup.PickupSummaryDto;
import app.reloop.dto.pickup.SchedulePickupRequest;
import app.reloop.dto.pickup.StatusUpdateRequest;
import app.reloop.dto.waste.WasteCategoryDto;
import app.reloop.entity.CollectedWaste;
import app.reloop.entity.CollectionPartner;
import app.reloop.entity.PickupRequest;
import app.reloop.entity.PickupRequest.PickupStatus;
import app.reloop.entity.PickupRequest.TimeSlot;
import app.reloop.entity.User;
import app.reloop.entity.UserProfile;
import app.reloop.entity.WasteCategory;
import app.reloop.exception.BadRequestException;
import app.reloop.exception.ConflictException;
import app.reloop.exception.ForbiddenException;
import app.reloop.exception.NotFoundException;
import app.reloop.repository.CollectedWasteRepository;
import app.reloop.repository.CollectionPartnerRepository;
import app.reloop.repository.PickupRequestRepository;
import app.reloop.repository.UserProfileRepository;
import app.reloop.repository.WasteCategoryRepository;
import app.reloop.security.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PickupService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PickupRequestRepository pickupRequestRepository;
    private final CollectedWasteRepository collectedWasteRepository;
    private final CollectionPartnerRepository collectionPartnerRepository;
    private final WasteCategoryRepository wasteCategoryRepository;
    private final UserProfileRepository userProfileRepository;
    private final ImageStorageService imageStorageService;
    private final CollectorService collectorService;
    private final NotificationService notificationService;
    private final Clock appClock;

    // ------------------------------------------------------------------ create / read

    @Transactional
    public PickupDto create(User user, CreatePickupRequest request, MultipartFile photo) {
        WasteCategory category = wasteCategoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new NotFoundException("Unknown waste category"));
        if (!category.isActive()) {
            throw new BadRequestException("This waste category is no longer available");
        }

        PickupRequest pickup = new PickupRequest();
        pickup.setCode(generateUniqueCode());
        pickup.setUser(user);
        pickup.setCategory(category);
        pickup.setEstimatedQuantityKg(request.estimatedQuantityKg());
        pickup.setAddress(request.address().trim());
        pickup.setCity(request.city().trim());
        pickup.setPincode(request.pincode());
        pickup.setLatitude(request.latitude());
        pickup.setLongitude(request.longitude());
        pickup.setPickupDate(request.pickupDate());
        pickup.setTimeSlot(TimeSlot.valueOf(request.timeSlot()));
        pickup.setNotes(request.notes());
        pickup.setStatus(PickupStatus.REQUESTED);

        if (photo != null && !photo.isEmpty()) {
            ImageStorageService.StoredImage stored = imageStorageService.store(photo, "pickups");
            pickup.setPhotoUrl(stored.publicUrl());
        }

        return toDto(pickupRequestRepository.save(pickup));
    }

    @Transactional(readOnly = true)
    public Page<PickupDto> listMine(User user, String status, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 50));
        Page<PickupRequest> result;
        if (status != null && !status.isBlank()) {
            PickupStatus parsed = parseStatus(status);
            result = pickupRequestRepository.findAllByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), parsed, pageable);
        } else {
            result = pickupRequestRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId(), pageable);
        }
        return result.map(this::toDto);
    }

    @Transactional(readOnly = true)
    public PickupDto getByCode(User actor, String code) {
        PickupRequest pickup = findPickup(code);
        assertCanView(actor, pickup);
        return toDto(pickup);
    }

    // ------------------------------------------------------------------ user actions

    @Transactional
    public PickupDto cancelByUser(User user, String code) {
        PickupRequest pickup = findPickup(code);
        if (!pickup.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("You can only cancel your own pickup requests");
        }
        if (pickup.getStatus() != PickupStatus.REQUESTED) {
            throw new ConflictException("Invalid status transition: " + pickup.getStatus()
                    + " → CANCELLED. Requests can only be cancelled while unassigned.");
        }
        pickup.setStatus(PickupStatus.CANCELLED);
        pickup.setCancelledAt(Instant.now());
        pickup.setCancelReason("Cancelled by requester");
        return toDto(pickupRequestRepository.save(pickup));
    }

    // ------------------------------------------------------------------ collector actions

    @Transactional(readOnly = true)
    public Page<PickupDto> assignedTo(CollectionPartner partner, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 50));
        return pickupRequestRepository.findAllByCollectorIdOrderByCreatedAtDesc(partner.getId(), pageable)
                .map(this::toDto);
    }

    /**
     * The open pool. Deliberately returns redacted {@link PickupSummaryDto} records: a collector
     * deciding whether to take a job needs the material, the city and a rough distance — not the
     * household's street address, coordinates, gate notes and photo. Full detail is released once
     * the request is assigned to them (see {@link #getByCode}).
     *
     * <p>Filtering is done here rather than in the client so that a collector never has to download
     * every city's requests to find the ones they can serve. {@code city} and {@code materialCode}
     * are pushed into SQL. {@code maxDistanceKm} cannot be — distance is a great-circle calculation
     * over stored coordinates — so a radius filter is applied to the whole matching set and then
     * paginated, never silently against one page of it.
     */
    @Transactional(readOnly = true)
    public Page<PickupSummaryDto> availableSummaries(BigDecimal callerLat, BigDecimal callerLng,
                                                    String city, String materialCode,
                                                    BigDecimal maxDistanceKm, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 50);
        Specification<PickupRequest> filters = openPoolSpecification(city, materialCode);

        if (maxDistanceKm == null) {
            Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
            return pickupRequestRepository.findAll(filters, pageable)
                    .map(pickup -> toSummary(pickup, callerLat, callerLng));
        }

        if (callerLat == null || callerLng == null) {
            throw new BadRequestException(
                    "A radius filter needs lat and lng so the distance to each request can be measured");
        }
        if (maxDistanceKm.signum() <= 0) {
            throw new BadRequestException("Radius must be greater than zero");
        }
        List<PickupSummaryDto> within = pickupRequestRepository
                .findAll(filters, Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(pickup -> toSummary(pickup, callerLat, callerLng))
                .filter(summary -> summary.approximateDistanceKm() != null
                        && summary.approximateDistanceKm().compareTo(maxDistanceKm) <= 0)
                .sorted(Comparator.comparing(PickupSummaryDto::approximateDistanceKm))
                .toList();
        int from = Math.min(safePage * safeSize, within.size());
        int to = Math.min(from + safeSize, within.size());
        return new PageImpl<>(within.subList(from, to), PageRequest.of(safePage, safeSize), within.size());
    }

    /** Filter values that exist in the pool right now, so the collector UI offers nothing empty. */
    @Transactional(readOnly = true)
    public OpenPoolFiltersDto openPoolFilters() {
        return new OpenPoolFiltersDto(
                pickupRequestRepository.findOpenCities(PickupStatus.REQUESTED),
                pickupRequestRepository.findOpenMaterialCodes(PickupStatus.REQUESTED));
    }

    /** Unassigned requests, narrowed by the optional city and material filters. */
    private Specification<PickupRequest> openPoolSpecification(String city, String materialCode) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), PickupStatus.REQUESTED));
            predicates.add(cb.isNull(root.get("collector")));
            if (city != null && !city.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("city")),
                        "%" + city.trim().toLowerCase(Locale.ROOT) + "%"));
            }
            if (materialCode != null && !materialCode.isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("category").get("code")),
                        materialCode.trim().toUpperCase(Locale.ROOT)));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    /**
     * Claims a request for a collector. {@code PickupRequest.version} makes this safe under
     * concurrency: if two collectors accept at the same instant, the second update matches no rows
     * and the transaction fails with an optimistic-lock conflict (HTTP 409) instead of both
     * reporting success. Both the notification and the assignment roll back together.
     */
    @Transactional
    public PickupDto accept(CollectionPartner partner, String code) {
        PickupRequest pickup = findPickup(code);
        if (pickup.getStatus() != PickupStatus.REQUESTED || pickup.getCollector() != null) {
            throw new ConflictException("Invalid status transition: " + pickup.getStatus()
                    + " → ACCEPTED. This request is no longer available.");
        }
        // Flush the claim immediately so a losing racer fails here, inside this method, rather than
        // at commit — the error still maps to a conflict either way, but this keeps the failure
        // attached to the action that caused it.
        pickupRequestRepository.saveAndFlush(pickup);
        pickup.setCollector(partner);
        pickup.setStatus(PickupStatus.ACCEPTED);
        pickup.setAcceptedAt(Instant.now());
        PickupDto dto = toDto(pickupRequestRepository.save(pickup));
        notificationService.create(pickup.getUser(), "PICKUP_ACCEPTED",
                "Your pickup request was accepted",
                pickup.getCollector().getOrganizationName() + " accepted request " + pickup.getCode()
                        + ". They will contact you to confirm the pickup time.",
                pickup.getId());
        return dto;
    }

    @Transactional
    public PickupDto schedule(CollectionPartner partner, String code, SchedulePickupRequest request) {
        PickupRequest pickup = findPickup(code);
        assertAssignedCollector(partner, pickup);
        if (pickup.getStatus() != PickupStatus.ACCEPTED) {
            throw new ConflictException("Invalid status transition: " + pickup.getStatus()
                    + " → SCHEDULED. Only accepted requests can be scheduled.");
        }
        if (request.scheduledAt().isBefore(Instant.now())) {
            throw new BadRequestException("Scheduled time must be in the future");
        }
        pickup.setStatus(PickupStatus.SCHEDULED);
        pickup.setScheduledAt(request.scheduledAt());
        PickupDto dto = toDto(pickupRequestRepository.save(pickup));
        notificationService.create(pickup.getUser(), "PICKUP_SCHEDULED",
                "Your pickup has been scheduled",
                "Request " + pickup.getCode() + " is scheduled for "
                        + request.scheduledAt().toString().replace('T', ' ').substring(0, 16) + " UTC.",
                pickup.getId());
        return dto;
    }

    @Transactional
    public PickupDto collect(CollectionPartner partner, String code, CollectPickupRequest request) {
        PickupRequest pickup = findPickup(code);
        assertAssignedCollector(partner, pickup);
        if (pickup.getStatus() != PickupStatus.ACCEPTED && pickup.getStatus() != PickupStatus.SCHEDULED) {
            throw new ConflictException("Invalid status transition: " + pickup.getStatus()
                    + " → PICKED_UP. Only accepted or scheduled requests can be collected.");
        }
        if (collectedWasteRepository.existsByPickupRequestId(pickup.getId())) {
            throw new ConflictException("A collection record already exists for this pickup");
        }
        pickup.setStatus(PickupStatus.PICKED_UP);
        pickup.setActualQuantityKg(request.actualQuantityKg());
        pickup.setPickedUpAt(Instant.now());

        CollectedWaste record = new CollectedWaste();
        record.setPickupRequest(pickup);
        record.setCollector(partner);
        record.setUser(pickup.getUser());
        record.setCategory(pickup.getCategory());
        record.setQuantityKg(request.actualQuantityKg());
        record.setCollectionDate(Instant.now());
        record.setNotes(request.notes());
        collectedWasteRepository.save(record);

        PickupDto dto = toDto(pickupRequestRepository.save(pickup));
        notificationService.create(pickup.getUser(), "PICKUP_COLLECTED",
                "Your waste collection was recorded",
                pickup.getActualQuantityKg() + " kg of " + pickup.getCategory().getName()
                        + " was collected for request " + pickup.getCode() + ".",
                pickup.getId());
        return dto;
    }

    @Transactional
    public PickupDto updateProcessingStatus(User actor, String code, StatusUpdateRequest request) {
        PickupRequest pickup = findPickup(code);
        PickupStatus target;
        try {
            target = PickupStatus.valueOf(request.status().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown status: " + request.status());
        }
        if (target != PickupStatus.PROCESSING && target != PickupStatus.RECOVERED
                && target != PickupStatus.RECYCLED) {
            throw new BadRequestException("Only PROCESSING, RECOVERED or RECYCLED can be set here");
        }
        if (actor.getRole() != Role.ADMIN) {
            CollectionPartner partner = collectorService.verifiedPartnerOf(actor);
            assertAssignedCollector(partner, pickup);
        }
        boolean processing = pickup.getStatus() == PickupStatus.PICKED_UP && target == PickupStatus.PROCESSING;
        boolean recovered = pickup.getStatus() == PickupStatus.PROCESSING && target == PickupStatus.RECOVERED;
        boolean recycled = pickup.getStatus() == PickupStatus.PROCESSING && target == PickupStatus.RECYCLED;
        if (!processing && !recovered && !recycled) {
            throw new ConflictException("Invalid status transition: " + pickup.getStatus()
                    + " → " + target);
        }
        pickup.setStatus(target);
        if (target == PickupStatus.PROCESSING) {
            pickup.setProcessingAt(Instant.now());
        } else {
            // RECOVERED and RECYCLED are both terminal; share the completion timestamp.
            pickup.setRecoveredAt(Instant.now());
        }
        PickupDto dto = toDto(pickupRequestRepository.save(pickup));
        // An admin can drive this transition too, and the organisation is read only for the
        // notification text — never assume the collector is set.
        String organisation = pickup.getCollector() != null
                ? pickup.getCollector().getOrganizationName()
                : "the collection partner";
        String title;
        String message;
        String type;
        switch (target) {
            case PROCESSING -> {
                title = "Your collection has moved to processing";
                message = "Request " + pickup.getCode() + " is now being processed by " + organisation + ".";
                type = "PICKUP_PROCESSING";
            }
            case RECYCLED -> {
                title = "Your waste has been recycled";
                message = "Request " + pickup.getCode() + " was accepted into a recycling process by "
                        + organisation + ". Thank you for closing the loop!";
                type = "PICKUP_RECYCLED";
            }
            default -> {
                title = "Your waste has been recovered";
                message = "Request " + pickup.getCode() + " completed the recovery process. Thank you for closing the loop!";
                type = "PICKUP_RECOVERED";
            }
        }
        notificationService.create(pickup.getUser(), type, title, message, pickup.getId());
        return dto;
    }

    /** Redacted projection for the open pool. See {@link PickupSummaryDto}. */
    public PickupSummaryDto toSummary(PickupRequest pickup, BigDecimal callerLat, BigDecimal callerLng) {
        BigDecimal distance = null;
        if (callerLat != null && callerLng != null && pickup.getLatitude() != null && pickup.getLongitude() != null) {
            double km = GeoUtils.distanceKm(callerLat.doubleValue(), callerLng.doubleValue(),
                    GeoUtils.toDouble(pickup.getLatitude(), 0), GeoUtils.toDouble(pickup.getLongitude(), 0));
            // Coarse on purpose: enough to judge the trip, too rough to locate a home.
            distance = BigDecimal.valueOf(Math.max(1, Math.round(km)));
        }
        return new PickupSummaryDto(
                pickup.getCode(),
                pickup.getStatus() != null ? pickup.getStatus().name() : null,
                pickup.getCategory() != null ? WasteCategoryDto.from(pickup.getCategory()) : null,
                pickup.getEstimatedQuantityKg(),
                pickup.getCity(),
                pickup.getPickupDate(),
                pickup.getTimeSlot() != null ? pickup.getTimeSlot().name() : null,
                pickup.getCreatedAt(),
                distance);
    }

    @Transactional
    public PickupDto release(CollectionPartner partner, String code, String reason) {
        PickupRequest pickup = findPickup(code);
        assertAssignedCollector(partner, pickup);
        if (pickup.getStatus() != PickupStatus.ACCEPTED && pickup.getStatus() != PickupStatus.SCHEDULED) {
            throw new ConflictException("Invalid status transition: " + pickup.getStatus()
                    + " → REQUESTED. Only accepted or scheduled requests can be released.");
        }
        pickup.setCollector(null);
        pickup.setStatus(PickupStatus.REQUESTED);
        pickup.setAcceptedAt(null);
        pickup.setScheduledAt(null);
        return toDto(pickupRequestRepository.save(pickup));
    }

    // ------------------------------------------------------------------ collector dashboard

    @Transactional(readOnly = true)
    public CollectorDashboardDto dashboard(CollectionPartner partner) {
        long available = pickupRequestRepository.countByStatus(PickupStatus.REQUESTED);
        long active = pickupRequestRepository.countByCollectorIdAndStatusIn(partner.getId(),
                Set.of(PickupStatus.ACCEPTED, PickupStatus.SCHEDULED));
        long completed = pickupRequestRepository.countByCollectorIdAndStatusIn(partner.getId(),
                Set.of(PickupStatus.PICKED_UP, PickupStatus.PROCESSING, PickupStatus.RECOVERED));
        // "Today" is the collector's calendar day in the application's operating zone.
        Instant startOfDay = LocalDate.now(appClock).atStartOfDay(appClock.getZone()).toInstant();
        long today = pickupRequestRepository.countByCollectorSince(partner.getId(),
                Set.of(PickupStatus.PICKED_UP, PickupStatus.PROCESSING, PickupStatus.RECOVERED), startOfDay);
        var totalKg = collectedWasteRepository.sumKgByCollector(partner.getId());
        long totalCollections = collectedWasteRepository.countByCollector(partner.getId());
        return new CollectorDashboardDto(available, active, completed, today,
                totalKg == null ? java.math.BigDecimal.ZERO : totalKg, totalCollections);
    }

    // ------------------------------------------------------------------ helpers

    private PickupRequest findPickup(String code) {
        return pickupRequestRepository.findByCode(code)
                .orElseThrow(() -> new NotFoundException("Pickup request " + code + " not found"));
    }

    private void assertCanView(User actor, PickupRequest pickup) {
        if (actor.getRole() == Role.ADMIN) {
            return;
        }
        if (pickup.getUser().getId().equals(actor.getId())) {
            return;
        }
        if (pickup.getCollector() != null && pickup.getCollector().getUser() != null
                && pickup.getCollector().getUser().getId().equals(actor.getId())) {
            return;
        }
        throw new ForbiddenException("You do not have access to this pickup request");
    }

    private void assertAssignedCollector(CollectionPartner partner, PickupRequest pickup) {
        if (pickup.getCollector() == null || pickup.getCollector().getUser() == null
                || !partner.getId().equals(pickup.getCollector().getId())) {
            throw new ForbiddenException("This request is not assigned to your organisation");
        }
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            int number = 100_000 + RANDOM.nextInt(900_000);
            String code = "RL-" + number;
            if (!pickupRequestRepository.existsByCode(code)) {
                return code;
            }
        }
        throw new ConflictException("Could not generate a unique pickup code, please retry");
    }

    private PickupStatus parseStatus(String status) {
        try {
            return PickupStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown status: " + status);
        }
    }

    public PickupDto toDto(PickupRequest pickup) {
        String requesterName = userProfileRepository.findByUserId(pickup.getUser().getId())
                .map(UserProfile::getFullName)
                .orElse(pickup.getUser().getEmail());
        return new PickupDto(
                pickup.getCode(),
                pickup.getStatus() != null ? pickup.getStatus().name() : null,
                pickup.getCategory() != null ? WasteCategoryDto.from(pickup.getCategory()) : null,
                pickup.getEstimatedQuantityKg(),
                pickup.getActualQuantityKg(),
                pickup.getAddress(),
                pickup.getCity(),
                pickup.getPincode(),
                pickup.getLatitude(),
                pickup.getLongitude(),
                pickup.getPickupDate(),
                pickup.getTimeSlot() != null ? pickup.getTimeSlot().name() : null,
                pickup.getPhotoUrl(),
                pickup.getNotes(),
                requesterName,
                pickup.getCollector() != null ? pickup.getCollector().getOrganizationName() : null,
                pickup.getScheduledAt(),
                pickup.getAcceptedAt(),
                pickup.getPickedUpAt(),
                pickup.getProcessingAt(),
                pickup.getRecoveredAt(),
                pickup.getCancelledAt(),
                pickup.getCancelReason(),
                pickup.getCreatedAt(),
                pickup.getUpdatedAt());
    }
}
