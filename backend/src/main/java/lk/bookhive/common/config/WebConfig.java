package lk.bookhive.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Web MVC settings shared by all modules. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  public static final String API_PREFIX = "/api/v1";

  // Every BookHive @RestController gets /api/v1 in front, so controllers declare only "/books".
  // Limited to our own package: library controllers (springdoc's /v3/api-docs) keep their paths.
  @Override
  public void configurePathMatch(PathMatchConfigurer configurer) {
    configurer.addPathPrefix(
        API_PREFIX,
        HandlerTypePredicate.forBasePackage("lk.bookhive")
            .and(HandlerTypePredicate.forAnnotation(RestController.class)));
  }
}
