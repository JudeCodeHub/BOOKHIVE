package lk.bookhive.community.event;

import java.util.UUID;

/** A contact form message was saved; the notification module emails it to the library. */
public record ContactMessageReceived(
    UUID messageId,
    String libraryEmail,
    String name,
    String email,
    String subject,
    String message,
    String language) {

  // The message text may be personal: keep it out of logs
  @Override
  public String toString() {
    return "ContactMessageReceived[messageId=" + messageId + "]";
  }
}
