package app.reloop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "waste_scans")
@Getter
@Setter
@NoArgsConstructor
public class WasteScan {

    public enum ScanSource { AI, MANUAL }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private WasteCategory category;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "detected_item", length = 200)
    private String detectedItem;

    @Column(precision = 4, scale = 3)
    private BigDecimal confidence;

    @Column(name = "recyclable")
    private Boolean recyclable;

    @Column(name = "hazardous")
    private Boolean hazardous;

    @Column(name = "disposal_instruction", length = 1000)
    private String disposalInstruction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ScanSource source;

    @Column(name = "ai_model", length = 60)
    private String aiModel;

    @Column(name = "raw_response", columnDefinition = "TEXT")
    private String rawResponse;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
