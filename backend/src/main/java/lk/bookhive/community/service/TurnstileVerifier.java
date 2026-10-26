package lk.bookhive.community.service;

import java.net.http.HttpClient;
import lk.bookhive.common.exception.FieldValidationException;
import lk.bookhive.common.exception.ServiceUnavailableException;
import lk.bookhive.community.config.TurnstileProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/**
 * Checks a Cloudflare Turnstile token with Cloudflare (server side: a token checked only in the
 * browser proves nothing). Each token works once and expires after a few minutes.
 */
@Component
@EnableConfigurationProperties(TurnstileProperties.class)
public class TurnstileVerifier {

  private static final Logger log = LoggerFactory.getLogger(TurnstileVerifier.class);

  private final TurnstileProperties properties;
  private final RestClient http;

  public TurnstileVerifier(TurnstileProperties properties) {
    this.properties = properties;
    JdkClientHttpRequestFactory factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(properties.timeout()).build());
    factory.setReadTimeout(properties.timeout());
    this.http = RestClient.builder().requestFactory(factory).build();
  }

  /**
   * @throws FieldValidationException CAPTCHA_FAILED: missing, expired or fake token
   * @throws ServiceUnavailableException CAPTCHA_UNAVAILABLE: Cloudflare didn't answer
   */
  public void verify(String token, String clientIp) {
    if (!properties.enabled()) {
      return;
    }
    if (token == null || token.isBlank()) {
      throw failed();
    }
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("secret", properties.secret());
    form.add("response", token);
    if (clientIp != null) {
      form.add("remoteip", clientIp);
    }
    JsonNode answer;
    try {
      answer =
          http.post()
              .uri(properties.verifyUrl())
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(form)
              .retrieve()
              .body(JsonNode.class);
    } catch (RestClientException e) {
      log.warn("Turnstile check failed: {}", e.getMessage());
      throw new ServiceUnavailableException(
          "CAPTCHA_UNAVAILABLE", "The spam check isn't answering. Please try again in a minute.");
    }
    if (answer == null || !answer.path("success").asBoolean(false)) {
      log.info(
          "Turnstile refused a token: {}",
          answer == null ? "no answer" : answer.path("error-codes"));
      throw failed();
    }
  }

  private static FieldValidationException failed() {
    return new FieldValidationException(
        "turnstileToken", "CAPTCHA_FAILED", "Please complete the check that you're not a robot.");
  }
}
