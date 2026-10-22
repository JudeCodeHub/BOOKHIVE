package lk.bookhive.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code bookhive.mail.*} settings. The SMTP server itself is configured with Spring's standard
 * {@code spring.mail.*} properties.
 *
 * @param from sender shown to users, e.g. "BookHive <no-reply@bookhive.lk>"
 * @param frontendUrl website address used to build links in emails
 */
@ConfigurationProperties("bookhive.mail")
public record MailProperties(String from, String frontendUrl) {}
