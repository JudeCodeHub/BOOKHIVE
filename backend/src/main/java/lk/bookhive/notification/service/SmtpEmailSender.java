package lk.bookhive.notification.service;

import lk.bookhive.notification.config.MailProperties;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Sends through the SMTP server in spring.mail.* (Mailpit in dev: see http://localhost:8025). */
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
    SimpleMailMessage mail = new SimpleMailMessage();
    mail.setFrom(properties.from());
    mail.setTo(message.to());
    mail.setSubject(message.subject());
    mail.setText(message.text());
    mailSender.send(mail);
  }
}
