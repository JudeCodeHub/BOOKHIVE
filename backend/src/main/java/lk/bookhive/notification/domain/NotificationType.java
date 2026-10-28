package lk.bookhive.notification.domain;

import java.util.Set;

/**
 * Every message BookHive can send (ADR 0004, checklist step 305), with the channels it uses by
 * default and whether a member may switch it off (preferences, step 313). Security messages and
 * receipts always go out by email; reminders can be turned off per channel.
 *
 * <p>Each type has a template per channel and language (step 309) named after it, e.g. {@code
 * email/due-soon_si.html}.
 */
public enum NotificationType {
  // Account and security: email only, always sent
  EMAIL_VERIFICATION(Set.of(Channel.EMAIL), false),
  PASSWORD_RESET(Set.of(Channel.EMAIL), false),
  PASSWORD_CHANGED(Set.of(Channel.EMAIL), false),
  STAFF_INVITE(Set.of(Channel.EMAIL), false),

  // Membership
  MEMBERSHIP_APPROVED(Set.of(Channel.EMAIL, Channel.IN_APP), false),
  MEMBERSHIP_REJECTED(Set.of(Channel.EMAIL, Channel.IN_APP), false),
  MEMBERSHIP_EXPIRING(Set.of(Channel.EMAIL, Channel.IN_APP), true),

  // Loans
  CHECKOUT_RECEIPT(Set.of(Channel.EMAIL), false),
  DUE_SOON(Set.of(Channel.EMAIL, Channel.SMS, Channel.IN_APP), true),
  OVERDUE(Set.of(Channel.EMAIL, Channel.SMS, Channel.IN_APP), true),

  // Holds
  HOLD_READY(Set.of(Channel.EMAIL, Channel.SMS, Channel.IN_APP), true),
  HOLD_EXPIRING(Set.of(Channel.EMAIL, Channel.IN_APP), true),
  HOLD_EXPIRED(Set.of(Channel.IN_APP), true),

  // Money
  PAYMENT_RECEIVED(Set.of(Channel.EMAIL, Channel.IN_APP), false),

  // Library events (phase 11): a place from the waitlist and a cancellation always go out
  EVENT_PLACE_CONFIRMED(Set.of(Channel.EMAIL, Channel.IN_APP), false),
  EVENT_CANCELLED(Set.of(Channel.EMAIL, Channel.IN_APP), false),
  EVENT_REMINDER(Set.of(Channel.EMAIL, Channel.IN_APP), true);

  private final Set<Channel> defaultChannels;
  private final boolean optional;

  NotificationType(Set<Channel> defaultChannels, boolean optional) {
    this.defaultChannels = defaultChannels;
    this.optional = optional;
  }

  /** Channels used unless the member switched some off (only for optional types). */
  public Set<Channel> defaultChannels() {
    return defaultChannels;
  }

  /** May the member turn this type off in their preferences? */
  public boolean optional() {
    return optional;
  }

  /** The template file stem: DUE_SOON -> "due-soon". */
  public String templateName() {
    return name().toLowerCase().replace('_', '-');
  }
}
