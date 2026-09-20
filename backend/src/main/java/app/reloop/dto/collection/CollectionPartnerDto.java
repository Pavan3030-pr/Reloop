package app.reloop.dto.collection;

import app.reloop.entity.PartnerStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CollectionPartnerDto(
        UUID id,
        String organizationName,
        String contactPerson,
        String phone,
        String email,
        String address,
        String city,
        String pincode,
        String operatingHours,
        String registrationNumber,
        PartnerStatus status,
        String rejectionReason,
        Instant verifiedAt,
        String userEmail,
        List<String> materialCodes,
        Instant createdAt
) {}
