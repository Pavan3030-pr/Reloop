package app.reloop.service;

import app.reloop.dto.admin.AdminAnalyticsDto;
import app.reloop.dto.admin.AdminUserDto;
import app.reloop.dto.admin.SaveCategoryRequest;
import app.reloop.dto.admin.SaveCollectionPointRequest;
import app.reloop.dto.collection.CollectionPartnerDto;
import app.reloop.dto.collection.CollectionPointDto;
import app.reloop.dto.pickup.PickupDto;
import app.reloop.dto.waste.WasteCategoryDto;
import app.reloop.entity.CollectionPartner;
import app.reloop.entity.CollectionPoint;
import app.reloop.entity.PartnerStatus;
import app.reloop.entity.PickupRequest;
import app.reloop.entity.PickupRequest.PickupStatus;
import app.reloop.entity.User;
import app.reloop.entity.UserProfile;
import app.reloop.entity.WasteCategory;
import app.reloop.exception.BadRequestException;
import app.reloop.exception.ConflictException;
import app.reloop.exception.NotFoundException;
import app.reloop.repository.CollectionPartnerRepository;
import app.reloop.repository.CollectionPointRepository;
import app.reloop.repository.CollectedWasteRepository;
import app.reloop.repository.NotificationRepository;
import app.reloop.repository.PickupRequestRepository;
import app.reloop.repository.UserProfileRepository;
import app.reloop.repository.UserRepository;
import app.reloop.repository.WasteCategoryRepository;
import app.reloop.security.Role;
import app.reloop.security.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final CollectionPartnerRepository collectionPartnerRepository;
    private final PickupRequestRepository pickupRequestRepository;
    private final CollectionPointRepository collectionPointRepository;
    private final WasteCategoryRepository wasteCategoryRepository;
    private final CollectedWasteRepository collectedWasteRepository;
    private final NotificationRepository notificationRepository;
    private final CollectorService collectorService;
    private final NotificationService notificationService;
    private final PickupService pickupService;
    private final CollectionPointService collectionPointService;

    // ------------------------------------------------------------------ users

    @Transactional(readOnly = true)
    public Page<AdminUserDto> listUsers(String query, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));
        String normalized = query == null || query.isBlank() ? null : query.trim();
        return userRepository.searchDirectory(normalized, pageable).map(user -> {
            String fullName = userProfileRepository.findByUserId(user.getId())
                    .map(UserProfile::getFullName).orElse(null);
            return AdminUserDto.from(user, fullName);
        });
    }

    @Transactional
    public AdminUserDto setUserStatus(UUID userId, String status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        if (user.getRole() == Role.ADMIN) {
            throw new BadRequestException("Admin accounts cannot be disabled here");
        }
        user.setStatus(UserStatus.valueOf(status));
        return AdminUserDto.from(user, userProfileRepository.findByUserId(user.getId())
                .map(UserProfile::getFullName).orElse(null));
    }

    // ------------------------------------------------------------------ collectors

    @Transactional(readOnly = true)
    public Page<CollectionPartnerDto> listPartners(String status, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));
        PartnerStatus parsed = null;
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            try {
                parsed = PartnerStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Unknown partner status: " + status);
            }
        }
        Page<app.reloop.entity.CollectionPartner> partners = parsed == null
                ? collectionPartnerRepository.findAllByOrderByCreatedAtDesc(pageable)
                : collectionPartnerRepository.searchByStatusAdmin(parsed, pageable);
        return partners.map(this::toPartnerDto);
    }

    @Transactional
    public CollectionPartnerDto verifyPartner(UUID partnerId, User admin) {
        CollectionPartner partner = collectorService.verifyPartner(partnerId, admin);
        if (partner.getUser() != null) {
            notificationService.create(partner.getUser(), "COLLECTOR_VERIFIED",
                    "Your collector application was approved",
                    partner.getOrganizationName() + " is now a verified collector on ReLoop.",
                    partner.getId());
        }
        return toPartnerDto(partner);
    }

    @Transactional
    public CollectionPartnerDto rejectPartner(UUID partnerId, String reason, User admin) {
        CollectionPartner partner = collectorService.rejectPartner(partnerId, reason);
        if (partner.getUser() != null) {
            notificationService.create(partner.getUser(), "COLLECTOR_REJECTED",
                    "Your collector application was rejected",
                    "Reason: " + partner.getRejectionReason(),
                    partner.getId());
        }
        return toPartnerDto(partner);
    }

    // ------------------------------------------------------------------ pickups

    @Transactional(readOnly = true)
    public Page<PickupDto> listPickups(String status, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));
        Page<PickupRequest> result;
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            PickupStatus parsed;
            try {
                parsed = PickupStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Unknown pickup status: " + status);
            }
            result = pickupRequestRepository.findAllByStatusOrderByCreatedAtDesc(parsed, pageable);
        } else {
            result = pickupRequestRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        return result.map(pickupService::toDto);
    }

    // ------------------------------------------------------------------ collection points

    @Transactional
    public CollectionPointDto createCollectionPoint(User admin, SaveCollectionPointRequest request) {
        CollectionPoint point = new CollectionPoint();
        applyCollectionPoint(point, request);
        point.setVerified(true);
        point.setSource(CollectionPoint.PointSource.ADMIN);
        point.setCreatedBy(admin);
        return collectionPointService.toDto(collectionPointRepository.save(point), null, null);
    }

    @Transactional
    public CollectionPointDto updateCollectionPoint(UUID id, SaveCollectionPointRequest request) {
        CollectionPoint point = collectionPointRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Collection point not found"));
        applyCollectionPoint(point, request);
        return collectionPointService.toDto(point, null, null);
    }

    @Transactional
    public void deleteCollectionPoint(UUID id) {
        CollectionPoint point = collectionPointRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Collection point not found"));
        point.setActive(false);
        collectionPointRepository.save(point);
    }

    private void applyCollectionPoint(CollectionPoint point, SaveCollectionPointRequest request) {
        point.setName(request.name().trim());
        point.setAddress(request.address().trim());
        point.setCity(request.city().trim());
        point.setPincode(request.pincode());
        point.setLatitude(request.latitude());
        point.setLongitude(request.longitude());
        point.setOperatingHours(request.operatingHours());
        point.setContactPhone(request.contactPhone());
        point.setMaterials(new java.util.HashSet<>(resolveMaterials(request.materialCodes())));
    }

    // ------------------------------------------------------------------ categories

    @Transactional
    public WasteCategoryDto createCategory(SaveCategoryRequest request) {
        if (wasteCategoryRepository.findByCodeIgnoreCase(request.code()).isPresent()) {
            throw new ConflictException("A category with this code already exists");
        }
        WasteCategory category = new WasteCategory();
        applyCategory(category, request);
        return WasteCategoryDto.from(wasteCategoryRepository.save(category));
    }

    @Transactional
    public WasteCategoryDto updateCategory(UUID id, SaveCategoryRequest request) {
        WasteCategory category = wasteCategoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Category not found"));
        wasteCategoryRepository.findByCodeIgnoreCase(request.code())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new ConflictException("A category with this code already exists");
                });
        applyCategory(category, request);
        return WasteCategoryDto.from(category);
    }

    @Transactional
    public WasteCategoryDto setCategoryActive(UUID id, boolean active) {
        WasteCategory category = wasteCategoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Category not found"));
        category.setActive(active);
        return WasteCategoryDto.from(category);
    }

    private void applyCategory(WasteCategory category, SaveCategoryRequest request) {
        category.setCode(request.code().trim().toUpperCase());
        category.setName(request.name().trim());
        category.setDescription(request.description());
        category.setColorHex(request.colorHex());
        category.setDefaultDisposalInstructions(request.defaultDisposalInstructions());
    }

    // ------------------------------------------------------------------ analytics

    @Transactional(readOnly = true)
    public AdminAnalyticsDto analytics() {
        Map<String, Long> pickupsByStatus = new TreeMap<>();
        long totalPickups = 0;
        for (PickupStatus status : PickupStatus.values()) {
            long count = pickupRequestRepository.countByStatus(status);
            pickupsByStatus.put(status.name(), count);
            totalPickups += count;
        }
        Map<String, BigDecimal> kgByCategory = new TreeMap<>();
        collectedWasteRepository.sumAllKgByCategory().forEach(t ->
                kgByCategory.put(t.getCode(), t.getKg() == null ? BigDecimal.ZERO
                        : t.getKg().setScale(2, RoundingMode.HALF_UP)));

        return new AdminAnalyticsDto(
                userRepository.count(),
                collectionPartnerRepository.findAllByStatus(PartnerStatus.VERIFIED).size(),
                collectionPartnerRepository.findAllByStatus(PartnerStatus.PENDING).size(),
                pickupsByStatus,
                totalPickups,
                collectedWasteRepository.countAll(),
                collectedWasteRepository.sumAllKg() == null ? BigDecimal.ZERO
                        : collectedWasteRepository.sumAllKg().setScale(2, RoundingMode.HALF_UP),
                kgByCategory);
    }

    // ------------------------------------------------------------------ helpers

    private List<WasteCategory> resolveMaterials(List<String> codes) {
        return codes.stream()
                .map(code -> wasteCategoryRepository.findByCodeIgnoreCase(code.trim())
                        .orElseThrow(() -> new BadRequestException("Unknown material: " + code)))
                .toList();
    }

    private CollectionPartnerDto toPartnerDto(CollectionPartner partner) {
        return new CollectionPartnerDto(
                partner.getId(),
                partner.getOrganizationName(),
                partner.getContactPerson(),
                partner.getPhone(),
                partner.getEmail(),
                partner.getAddress(),
                partner.getCity(),
                partner.getPincode(),
                partner.getOperatingHours(),
                partner.getRegistrationNumber(),
                partner.getStatus(),
                partner.getRejectionReason(),
                partner.getVerifiedAt(),
                partner.getUser() != null ? partner.getUser().getEmail() : null,
                partner.getMaterials().stream().map(WasteCategory::getCode).sorted().toList(),
                partner.getCreatedAt());
    }

}
