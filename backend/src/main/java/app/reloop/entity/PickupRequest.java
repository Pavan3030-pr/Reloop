package app.reloop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "pickup_requests", indexes = {
        @Index(name = "idx_pickup_requests_user", columnList = "user_id"),
        @Index(name = "idx_pickup_requests_collector", columnList = "collector_id"),
        @Index(name = "idx_pickup_requests_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
public class PickupRequest {

    public enum PickupStatus {
        REQUESTED,
        ACCEPTED,
        SCHEDULED,
        PICKED_UP,
        PROCESSING,
        /** Material came back into the loop. */
        RECOVERED,
        /** Material was accepted into a recycling process. Terminal, like RECOVERED. */
        RECYCLED,
        CANCELLED
    }

    public enum TimeSlot { MORNING, AFTERNOON, EVENING }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Optimistic lock. Without it, two collectors accepting the same request in the same instant
     * both pass the "is it still unassigned?" check and both succeed — one gets a confirmation for
     * a job the database awarded to the other, plus a duplicate notification to the resident.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    @Column(nullable = false, unique = true, length = 16)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collector_id")
    private CollectionPartner collector;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private WasteCategory category;

    @Column(name = "estimated_quantity_kg", nullable = false, precision = 8, scale = 2)
    private BigDecimal estimatedQuantityKg;

    @Column(name = "actual_quantity_kg", precision = 8, scale = 2)
    private BigDecimal actualQuantityKg;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(nullable = false, length = 80)
    private String city;

    @Column(length = 12)
    private String pincode;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "pickup_date", nullable = false)
    private LocalDate pickupDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "time_slot", nullable = false, length = 20)
    private TimeSlot timeSlot;

    @Column(name = "photo_url")
    private String photoUrl;

    @Column(length = 1000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PickupStatus status = PickupStatus.REQUESTED;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "picked_up_at")
    private Instant pickedUpAt;

    @Column(name = "processing_at")
    private Instant processingAt;

    @Column(name = "recovered_at")
    private Instant recoveredAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (status == null) {
            status = PickupStatus.REQUESTED;
        }
    }

    @PreUpdate
    void onUpdate() {
        // timestamps handled by Hibernate
    }
}
