package vn.englishconversation.config;
import vn.englishconversation.config.features.FeatureEnabled;

import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration
@FeatureEnabled(FeatureEnabled.Feature.BOOTSTRAP)
public class BootstrapSecurityConfig {
  @Bean
  SecurityFilterChain scaffoldSecurity(HttpSecurity http) throws Exception {
    return http.authorizeHttpRequests(
            a ->
                a.requestMatchers(HttpMethod.GET, "/api/v1/system/health", "/api/v1/system/features")
                    .permitAll()
                    .anyRequest()
                    .denyAll())
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .exceptionHandling(
            e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .build();
  }
}
