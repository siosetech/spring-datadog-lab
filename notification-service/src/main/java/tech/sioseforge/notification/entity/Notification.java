package tech.sioseforge.notification.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String recipient;
    private String message;
    private String status;
    private Instant createdAt;

    public Notification() {}

    public Notification(String recipient, String message, String status, Instant createdAt) {
        this.recipient = recipient;
        this.message = message;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getRecipient() { return recipient; }
    public String getMessage() { return message; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setStatus(String status) { this.status = status; }
}
