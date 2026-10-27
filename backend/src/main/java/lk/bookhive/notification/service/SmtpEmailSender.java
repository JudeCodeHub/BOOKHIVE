package lk.bookhive.notification.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import lk.bookhive.notification.config.MailProperties;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Sends through the SMTP server in {@code spring.mail.*} (step 310): Mailpit in development (see
 * http://localhost:8025), Amazon SES's SMTP endpoint in production (application-prod.yml). With an
 * HTML version, the email is "multipart/alternative": the text part for simple mail apps, the HTML
 * for the rest. UTF-8 throughout, for Sinhala and Tamil.
 */
@Component
public class SmtpEmailSender implements EmailSender {

  private final JavaMailSender mailSender;
  private final MailProperties properties;

  public SmtpEmailSender(JavaMailSender mailSender, MailProperties properties) {
    this.mailSender = mailSender;
    this.properties = properties;
  }

  @Override
  public void send(EmailMessage message) {
    MimeMessage mime = mailSender.createMimeMessage();
    try {
      boolean multipart = message.html() != null;
      MimeMessageHelper helper =
          new MimeMessageHelper(mime, multipart, StandardCharsets.UTF_8.name());
      helper.setFrom(properties.from());
      helper.setTo(message.to());
      helper.setSubject(message.subject());
      if (multipart) {
        helper.setText(message.text(), message.html());
      } else {
        helper.setText(message.text(), false);
      }
    } catch (MessagingException e) {
      throw new MailPreparationException("Could not build the email", e);
    }
    mailSender.send(mime);
  }
}
