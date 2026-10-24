package com.tiet.docket.workflow;

import com.tiet.docket.model.Application;
import com.tiet.docket.model.Notification;
import com.tiet.docket.model.Role;
import com.tiet.docket.model.User;
import com.tiet.docket.repository.NotificationRepository;
import com.tiet.docket.repository.UserRepository;
import org.springframework.stereotype.Service;    // Spring's @Service, NOT our model.Service entity
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Handles all notification logic for the Docket application.
 *
 * A "notification" is a short message shown to a user in the bell-icon
 * dropdown.  This service creates them, marks them as read, and counts
 * unread ones.
 *
 * NOTE ON NAME CLASH: the class annotation is Spring's @Service.
 * Our entity class is com.tiet.docket.model.Service.  We do NOT
 * import that entity here to avoid a compile-time name clash.
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    // Spring injects both repositories through the constructor.
    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    // ================================================================
    // WRITE methods  (all @Transactional so they commit together)
    // ================================================================

    /**
     * Sends a notification to exactly one user.
     *
     * @param user        the recipient
     * @param application the related application (may be null)
     * @param message     the text to show (max 500 chars as per the entity)
     * @return the saved Notification row
     */
    @Transactional
    public Notification notifyUser(User user, Application application, String message) {
        Notification n = new Notification();
        n.setUser(user);
        n.setApplication(application);
        n.setMessage(message);
        return notificationRepository.save(n);
    }

    /**
     * Sends one notification to every active user with the given role.
     * If {@code department} is not null and not blank, only users in that
     * department receive the notification.
     * Inactive users (user.isActive() == false) are always skipped.
     *
     * @param role       target role (e.g. Role.ADMIN_OFFICER)
     * @param department optional department filter (pass null to skip filtering)
     * @param application related application (may be null)
     * @param message    the text to show
     */
    @Transactional
    public void notifyRole(Role role, String department,
                           Application application, String message) {

        // Get all users with this role from the database.
        List<User> candidates = userRepository.findByRole(role);

        for (User u : candidates) {
            // Skip deactivated accounts.
            if (!u.isActive()) {
                continue;
            }
            // If a department filter is given, skip users in other departments.
            if (department != null && !department.isBlank()) {
                if (!department.equalsIgnoreCase(u.getDepartment())) {
                    continue;
                }
            }
            // Save one notification row for this user.
            notifyUser(u, application, message);
        }
    }

    /**
     * Marks one notification as read.
     * Throws IllegalStateException if the notification does not belong to
     * the given user (so one user cannot mark another's notifications).
     *
     * @param notificationId the ID of the Notification row
     * @param user           the user who is marking it read
     */
    @Transactional
    public void markRead(Long notificationId, User user) {
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Notification not found: " + notificationId));

        // Security check: the notification must belong to this user.
        if (!n.getUser().getId().equals(user.getId())) {
            throw new IllegalStateException(
                    "You cannot mark another user's notification as read");
        }
        n.setRead(true);
        notificationRepository.save(n);
    }

    /**
     * Marks all unread notifications for a user as read in one go.
     *
     * @param user the owner of the notifications
     */
    @Transactional
    public void markAllRead(User user) {
        List<Notification> unread = notificationRepository.findByUserAndReadFalse(user);
        for (Notification n : unread) {
            n.setRead(true);
            notificationRepository.save(n);
        }
    }

    // ================================================================
    // READ methods  (readOnly=true is a performance hint to the DB)
    // ================================================================

    /**
     * Returns all notifications for a user, newest first,
     * exactly as they should appear in the bell dropdown.
     *
     * @param user the recipient
     * @return list of notifications sorted by createdAt descending
     */
    @Transactional(readOnly = true)
    public List<Notification> getForUser(User user) {
        return notificationRepository.findByUserOrderByCreatedAtDesc(user);
    }

    /**
     * Returns the count of unread notifications (the number shown on the bell).
     *
     * @param user the recipient
     * @return number of unread notifications
     */
    @Transactional(readOnly = true)
    public long countUnread(User user) {
        return notificationRepository.countByUserAndReadFalse(user);
    }
}
