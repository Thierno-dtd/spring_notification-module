package module.notification.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notification_types")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationType {

    @Id
    @Column(length = 50)
    private String code;

    @Column(nullable = false)
    private String label;

    private String description;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isActive = true;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isSystemDefined = false;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}