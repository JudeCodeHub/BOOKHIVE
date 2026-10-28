package lk.bookhive.notification.config;

import java.nio.charset.StandardCharsets;
import lk.bookhive.notification.service.MessageBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * The engine that renders emails (checklist step 309): HTML templates in {@code templates/email/},
 * words from {@code messages/email*.properties} in English, Sinhala and Tamil. Its own engine and
 * message bundle, separate from anything web-related.
 */
@Configuration
public class EmailTemplateConfig {

  @Bean
  SpringTemplateEngine emailTemplateEngine() {
    ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("templates/email/");
    resolver.setSuffix(".html");
    resolver.setTemplateMode(TemplateMode.HTML);
    resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
    resolver.setCacheable(true);

    ResourceBundleMessageSource messages = MessageBundles.bundle("messages/email");

    SpringTemplateEngine engine = new SpringTemplateEngine();
    engine.setTemplateResolver(resolver);
    engine.setTemplateEngineMessageSource(messages);
    return engine;
  }
}
