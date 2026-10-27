/**
 * Notifications: email, SMS and in-app messages (ADR 0004).
 *
 * <p>Other modules never call this one: they publish events. Listeners here turn each event into a
 * {@link lk.bookhive.notification.service.NotificationRequest} after the transaction commits and
 * hand it to the {@link lk.bookhive.notification.service.Notifier}, which applies the member's
 * preferences, stores in-app messages and queues email/SMS in the outbox. Daily jobs publish the
 * time-based reminders (due soon, overdue, expiring).
 */
package lk.bookhive.notification;
