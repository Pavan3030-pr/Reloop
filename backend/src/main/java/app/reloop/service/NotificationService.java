package app.reloop.service;

import app.reloop.entity.Notification;
import app.reloop.entity.User;
import app.reloop.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional
    public void create(User user, String type, String title, String message, UUID referenceId) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setReferenceId(referenceId);
        notificationRepository.save(notification);
    }

    @Transactional
    public void create(User user, String type, String title, String message) {
        create(user, type, title, message, null);
    }

    @Transactional(readOnly = true)
    public Page<NotificationDtoHolder> list(User user, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 50));
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable)
                .map(NotificationDtoHolder::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount(User user) {
        return notificationRepository.countByUserIdAndReadFalse(user.getId());
    }

    @Transactional
    public void markRead(User user, UUID id) {
        Notification notification = notificationRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new app.reloop.exception.NotFoundException("Notification not found"));
        notification.setRead(true);
    }

    @Transactional
    public void markAllRead(User user) {
        notificationRepository.markAllRead(user.getId());
    }

    /** Small holder to avoid DTO mapping in the service layer's generic list API. */
    public record NotificationDtoHolder(
            UUID id, String type, String title, String message, UUID referenceId, boolean read, Instant createdAt) {

        public static NotificationDtoHolder from(Notification n) {
            return new NotificationDtoHolder(n.getId(), n.getType(), n.getTitle(), n.getMessage(),
                    n.getReferenceId(), n.isRead(), n.getCreatedAt());
        }
    }
}
