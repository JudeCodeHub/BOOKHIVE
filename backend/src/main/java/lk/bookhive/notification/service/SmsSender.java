package lk.bookhive.notification.service;

/**
 * Sends one text message (NOTIF-2). Implemented for a Sri Lankan gateway in step 311, behind a
 * feature flag; without an implementation, SMS messages in the outbox are marked FAILED with a
 * clear reason instead of being retried.
 */
public interface SmsSender {

  /**
   * @param phone the member's number as stored, e.g. 0771234567
   */
  void send(String phone, String text);
}
