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

  // Every @RestController gets /api/v1 in front, so controllers declare only "/books" etc.
  // Actuator (/actuator/health) is not a @RestController and stays unprefixed.
  @Override
  public void configurePathMatch(PathMatchConfigurer configurer) {
    configurer.addPathPrefix(API_PREFIX, HandlerTypePredicate.forAnnotation(RestController.class));
  }
}
