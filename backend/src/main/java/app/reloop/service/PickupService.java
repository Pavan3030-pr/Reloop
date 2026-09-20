package app.reloop.service;

import app.reloop.dto.pickup.CollectPickupRequest;
import app.reloop.dto.pickup.CollectorDashboardDto;
import app.reloop.dto.pickup.CreatePickupRequest;
import app.reloop.dto.pickup.PickupDto;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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

    @Transactional(readOnly = true)
    public Page<PickupDto> availableRequests(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 50));
        return pickupRequestRepository
                .findAllByStatusOrderByCreatedAtDesc(PickupStatus.REQUESTED, pageable)
                .map(this::toDto);
    }

    @Transactional
    public PickupDto accept(CollectionPartner partner, String code) {
        PickupRequest pickup = findPickup(code);
        if (pickup.getStatus() != PickupStatus.REQUESTED || pickup.getCollector() != null) {
            throw new ConflictException("Invalid status transition: " + pickup.getStatus()
                    + " → ACCEPTED. This request is no longer available.");
        }
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
        if (target != PickupStatus.PROCESSING && target != PickupStatus.RECOVERED) {
            throw new BadRequestException("Only PROCESSING or RECOVERED can be set here");
        }
        if (actor.getRole() != Role.ADMIN) {
            CollectionPartner partner = collectorService.verifiedPartnerOf(actor);
            assertAssignedCollector(partner, pickup);
        }
        boolean processing = pickup.getStatus() == PickupStatus.PICKED_UP && target == PickupStatus.PROCESSING;
        boolean recovered = pickup.getStatus() == PickupStatus.PROCESSING && target == PickupStatus.RECOVERED;
        if (!processing && !recovered) {
            throw new ConflictException("Invalid status transition: " + pickup.getStatus()
                    + " → " + target);
        }
        pickup.setStatus(target);
        if (target == PickupStatus.PROCESSING) {
            pickup.setProcessingAt(Instant.now());
        } else {
            pickup.setRecoveredAt(Instant.now());
        }
        PickupDto dto = toDto(pickupRequestRepository.save(pickup));
        String title = target == PickupStatus.PROCESSING
                ? "Your collection has moved to processing"
                : "Your waste has been recovered";
        String message = target == PickupStatus.PROCESSING
                ? "Request " + pickup.getCode() + " is now being processed by "
                  + pickup.getCollector().getOrganizationName() + "."
                : "Request " + pickup.getCode() + " completed the recovery process. Thank you for closing the loop!";
        notificationService.create(pickup.getUser(),
                target == PickupStatus.PROCESSING ? "PICKUP_PROCESSING" : "PICKUP_RECOVERED",
                title, message, pickup.getId());
        return dto;
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
        Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
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
