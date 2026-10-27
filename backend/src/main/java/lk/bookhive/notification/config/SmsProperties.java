package lk.bookhive.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code bookhive.sms.*}: text messages through Notify.lk (NOTIF-2, checklist step 311). Off by
 * default; with {@code enabled: false} no SMS is queued at all.
 *
 * @param enabled the feature flag
 * @param url Notify.lk's send endpoint
 * @param userId from the Notify.lk settings page (keep it in .env, never in git)
 * @param apiKey from the Notify.lk settings page (secret)
 * @param senderId the approved sender name, e.g. "BookHive" ("NotifyDEMO" only for testing)
 */
@ConfigurationProperties("bookhive.sms")
public record SmsProperties(
    boolean enabled, String url, String userId, String apiKey, String senderId) {

  // Never print the key
  @Override
  public String toString() {
    return "SmsProperties[enabled=" + enabled + ", senderId=" + senderId + "]";
  }
}
