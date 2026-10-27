package lk.bookhive.notification.service;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import lk.bookhive.notification.config.SmsProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Sends SMS through Notify.lk (NOTIF-2, step 311; https://developer.notify.lk/api-endpoints). Only
 * created when {@code bookhive.sms.enabled=true}. Sinhala and Tamil need the unicode type. A
 * failure throws, so the outbox retries it later.
 */
@Component
@ConditionalOnProperty(name = "bookhive.sms.enabled", havingValue = "true")
public class NotifyLkSmsSender implements SmsSender {

  private final SmsProperties properties;
  private final RestClient http;

  public NotifyLkSmsSender(SmsProperties properties) {
    if (isBlank(properties.userId()) || isBlank(properties.apiKey())) {
      throw new IllegalStateException(
          "SMS is enabled but SMS_USER_ID / SMS_API_KEY are not set (see .env.example)");
    }
    this.properties = properties;
    JdkClientHttpRequestFactory factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    factory.setReadTimeout(Duration.ofSeconds(10));
    this.http = RestClient.builder().requestFactory(factory).build();
  }

  @Override
  public void send(String phone, String text) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("user_id", properties.userId());
    form.add("api_key", properties.apiKey());
    form.add("sender_id", properties.senderId());
    form.add("to", toInternational(phone));
    form.add("message", text);
    if (!isAscii(text)) {
      form.add("type", "unicode");
    }
    Map<?, ?> response =
        http.post()
            .uri(properties.url())
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(Map.class);
    if (response == null || !"success".equals(response.get("status"))) {
      throw new IllegalStateException("Notify.lk did not accept the SMS: " + response);
    }
  }

  /** 0771234567, +94 77 123 4567, 94771234567 -> 94771234567 (the format Notify.lk wants). */
  static String toInternational(String phone) {
    String digits = phone.replaceAll("\\D", "");
    if (digits.startsWith("94") && digits.length() == 11) {
      return digits;
    }
    if (digits.startsWith("0") && digits.length() == 10) {
      return "94" + digits.substring(1);
    }
    if (digits.length() == 9) {
      return "94" + digits;
    }
    throw new IllegalArgumentException("Not a Sri Lankan mobile number: " + phone);
  }

  private static boolean isAscii(String text) {
    return StandardCharsets.US_ASCII.newEncoder().canEncode(text);
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
