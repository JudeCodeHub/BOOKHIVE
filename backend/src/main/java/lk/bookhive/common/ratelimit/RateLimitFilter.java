package lk.bookhive.common.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import lk.bookhive.common.exception.TooManyRequestsException;
import lk.bookhive.common.ratelimit.RateLimitProperties.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Limits how often one client may call sensitive endpoints (login, register, forgot password),
 * against password guessing and spam. Over the limit: 429 TOO_MANY_REQUESTS with a Retry-After
 * header.
 *
 * <p>Clients are told apart by IP address. Behind a load balancer the real IP comes from
 * X-Forwarded-For (server.forward-headers-strategy=native in prod).
 *
 * <p>If Redis is unreachable the request is let through (fail open): a Redis outage must not stop
 * everyone from logging in. Account lockout still protects passwords meanwhile.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10) // right after RequestIdFilter, before Spring Security
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

  private final RateLimiter rateLimiter;
  private final RateLimitProperties properties;
  private final HandlerExceptionResolver exceptionResolver;

  public RateLimitFilter(
      RateLimiter rateLimiter,
      RateLimitProperties properties,
      @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
    this.rateLimiter = rateLimiter;
    this.properties = properties;
    this.exceptionResolver = exceptionResolver;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !properties.enabled() || !"POST".equals(request.getMethod());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    for (Map.Entry<String, Rule> entry : properties.rules().entrySet()) {
      Rule rule = entry.getValue();
      if (!rule.path().equals(request.getRequestURI())) {
        continue;
      }
      Duration wait = consume(entry.getKey(), rule, request.getRemoteAddr());
      if (!wait.isZero()) {
        long seconds = Math.max(1, (wait.toMillis() + 999) / 1000);
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(seconds));
        exceptionResolver.resolveException(
            request,
            response,
            null,
            new TooManyRequestsException(
                "Too many attempts. Please wait " + seconds + " seconds and try again."));
        return;
      }
    }
    chain.doFilter(request, response);
  }

  private Duration consume(String ruleName, Rule rule, String client) {
    try {
      return rateLimiter.tryConsume(ruleName, rule, client);
    } catch (RuntimeException e) {
      log.warn("Rate limiting unavailable, letting the request through: {}", e.toString());
      return Duration.ZERO;
    }
  }
}
