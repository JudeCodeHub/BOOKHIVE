package lk.bookhive.notification.domain;

/** PENDING: to send (first try or a retry); SENT: accepted by the provider; FAILED: gave up. */
public enum OutboxStatus {
  PENDING,
  SENT,
  FAILED
}
