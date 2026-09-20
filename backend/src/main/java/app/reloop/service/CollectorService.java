package app.reloop.service;

import app.reloop.dto.collection.ApplyCollectorRequest;
import app.reloop.dto.collection.CollectionPartnerDto;
import app.reloop.entity.CollectionPartner;
import app.reloop.entity.PartnerStatus;
import app.reloop.entity.User;
import app.reloop.entity.WasteCategory;
import app.reloop.exception.BadRequestException;
import app.reloop.exception.ConflictException;
import app.reloop.exception.NotFoundException;
import app.reloop.repository.CollectionPartnerRepository;
import app.reloop.repository.WasteCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CollectorService {

    private final CollectionPartnerRepository collectionPartnerRepository;
    private final WasteCategoryRepository wasteCategoryRepository;

    @Transactional
    public CollectionPartnerDto apply(User user, ApplyCollectorRequest request) {
        if (collectionPartnerRepository.findByUserId(user.getId()).isPresent()) {
            throw new ConflictException("A collector application already exists for this account");
        }
        Set<WasteCategory> materials = resolveMaterials(request.materialCodes());

        CollectionPartner partner = new CollectionPartner();
        partner.setUser(user);
        partner.setOrganizationName(request.organizationName().trim());
        partner.setContactPerson(request.contactPerson().trim());
        partner.setPhone(request.phone().trim());
        partner.setEmail(request.email() == null || request.email().isBlank() ? user.getEmail() : request.email().trim());
        partner.setAddress(request.address().trim());
        partner.setCity(request.city().trim());
        partner.setPincode(request.pincode());
        partner.setOperatingHours(request.operatingHours());
        partner.setRegistrationNumber(request.registrationNumber());
        partner.setStatus(PartnerStatus.PENDING);
        partner.setMaterials(materials);
        return toDto(collectionPartnerRepository.save(partner));
    }

    @Transactional(readOnly = true)
    public CollectionPartnerDto myApplication(User user) {
        return collectionPartnerRepository.findByUserId(user.getId())
                .map(this::toDto)
                .orElseThrow(() -> new NotFoundException("No collector application found for this account"));
    }

    @Transactional
    public CollectionPartner verifyPartner(UUID partnerId, User admin) {
        CollectionPartner partner = collectionPartnerRepository.findById(partnerId)
                .orElseThrow(() -> new NotFoundException("Collector application not found"));
        if (partner.getStatus() == PartnerStatus.VERIFIED) {
            throw new BadRequestException("This collector is already verified");
        }
        partner.setStatus(PartnerStatus.VERIFIED);
        partner.setVerifiedAt(java.time.Instant.now());
        partner.setVerifiedBy(admin);
        partner.setRejectionReason(null);
        if (partner.getUser() != null) {
            partner.getUser().setRole(app.reloop.security.Role.COLLECTOR);
        }
        return partner;
    }

    @Transactional
    public CollectionPartner rejectPartner(UUID partnerId, String reason) {
        CollectionPartner partner = collectionPartnerRepository.findById(partnerId)
                .orElseThrow(() -> new NotFoundException("Collector application not found"));
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("A rejection reason is required");
        }
        partner.setStatus(PartnerStatus.REJECTED);
        partner.setRejectionReason(reason.trim());
        if (partner.getUser() != null && partner.getUser().getRole() == app.reloop.security.Role.COLLECTOR) {
            partner.getUser().setRole(app.reloop.security.Role.USER);
        }
        return partner;
    }

    @Transactional(readOnly = true)
    public CollectionPartner verifiedPartnerOf(User user) {
        CollectionPartner partner = collectionPartnerRepository.findByUserId(user.getId())
                .orElseThrow(() -> new app.reloop.exception.ForbiddenException(
                        "Collector access requires an approved collector application"));
        if (partner.getStatus() != PartnerStatus.VERIFIED) {
            throw new app.reloop.exception.ForbiddenException(
                    "Your collector application is not verified yet (status: " + partner.getStatus() + ")");
        }
        return partner;
    }

    private Set<WasteCategory> resolveMaterials(List<String> codes) {
        Set<WasteCategory> materials = new HashSet<>();
        for (String code : codes) {
            WasteCategory category = wasteCategoryRepository.findByCodeIgnoreCase(code.trim())
                    .orElseThrow(() -> new BadRequestException("Unknown material: " + code));
            materials.add(category);
        }
        return materials;
    }

    private CollectionPartnerDto toDto(CollectionPartner partner) {
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
