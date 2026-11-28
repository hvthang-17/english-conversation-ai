package vn.englishconversation.config;
import vn.englishconversation.config.features.FeatureEnabled;

import java.util.List;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.password.*;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.session.*;
import org.springframework.security.web.context.*;
import org.springframework.security.web.csrf.*;
import vn.englishconversation.auth.security.AccountStateFilter;
import vn.englishconversation.common.exception.ApiErrors;
import vn.englishconversation.user.repository.UserRepository;

@Configuration(proxyBeanMethods = false)
@FeatureEnabled(FeatureEnabled.Feature.CORE)
@EnableScheduling
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
  }

  @Bean
  SecurityContextRepository contexts() {
    return new HttpSessionSecurityContextRepository();
  }

  @Bean
  CsrfTokenRepository csrfRepository() {
    return new HttpSessionCsrfTokenRepository();
  }

  @Bean
  SessionAuthenticationStrategy loginStrategy(CsrfTokenRepository csrf) {
    return new CompositeSessionAuthenticationStrategy(
        List.of(new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrf)));
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      SecurityContextRepository contexts,
      CsrfTokenRepository csrf,
      UserRepository users, org.springframework.core.env.Environment environment)
      throws Exception {
    var features = vn.englishconversation.config.features.FeatureSet.resolve(environment);
    return http.securityContext(
            c -> c.securityContextRepository(contexts).requireExplicitSave(true))
        .csrf(c -> c.csrfTokenRepository(csrf))
        .requestCache(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(request -> vn.englishconversation.config.features.DisabledFeatureRoutes.matches(request,
                        features)).denyAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/system/health", "/api/v1/system/features", "/api/v1/auth/csrf")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/password-reset/request", "/api/v1/auth/password-reset/confirm")
                    .permitAll()
                    .requestMatchers("/api/v1/auth/account", "/api/v1/profile/me/avatar", "/api/v1/profile/me/practice-schedule")
                    .authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/v1/profile/me/avatar/*")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login")
                    .permitAll()
                    .requestMatchers("/api/v1/admin/**")
                    .hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/v1/pronunciation/drills", "/api/v1/pronunciation/attempts", "/api/v1/pronunciation/attempts/*").authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/v1/pronunciation/drills/*/attempts").authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/v1/lessons/*/listening", "/api/v1/listening/exercises", "/api/v1/listening/attempts",
                        "/api/v1/listening/attempts/*", "/api/v1/listening/attempts/*/audio")
                    .authenticated()
                    .requestMatchers(HttpMethod.PUT, "/api/v1/listening/attempts/*/draft")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/v1/listening/exercises/*/attempts",
                        "/api/v1/listening/attempts/*/submit", "/api/v1/listening/attempts/*/abandon")
                    .authenticated()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/topics",
                        "/api/v1/lessons",
                        "/api/v1/lessons/*",
                        "/api/v1/lessons/*/materials",
                        "/api/v1/scenarios",
                        "/api/v1/scenarios/*")
                    .authenticated()
                    .requestMatchers(HttpMethod.PUT, "/api/v1/lessons/*/bookmarks/*")
                    .authenticated()
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/lessons/*/bookmarks/*")
                    .authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/v1/profile/me")
                    .authenticated()
                    .requestMatchers(HttpMethod.PUT, "/api/v1/profile/me")
                    .authenticated()
                    .requestMatchers("/api/v1/auth/me", "/api/v1/auth/password")
                    .authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/v1/practice-sessions/*",
                        "/api/v1/practice-sessions/*/feedback", "/api/v1/practice-sessions/*/audio",
                        "/api/v1/practice-sessions/*/audio/*", "/api/v1/practice-sessions/*/messages/*/explanation",
                        "/api/v1/practice-sessions/*/messages/*/hint")
                    .authenticated()
                    .requestMatchers(HttpMethod.PATCH, "/api/v1/practice-sessions/*/mode")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/v1/practice-sessions",
                        "/api/v1/practice-sessions/*/turns", "/api/v1/practice-sessions/*/hints",
                        "/api/v1/practice-sessions/*/finish", "/api/v1/practice-sessions/*/operations/*/retry",
                        "/api/v1/practice-sessions/*/operations/*/skip",
                        "/api/v1/practice-sessions/*/feedback", "/api/v1/practice-sessions/*/feedback/retry",
                        "/api/v1/practice-sessions/*/audio", "/api/v1/practice-sessions/*/audio/*/confirm",
                        "/api/v1/practice-sessions/*/messages/*/explanation")
                    .authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/v1/learning/progress", "/api/v1/learning/progress/lessons/*").hasRole("LEARNER")
                    .requestMatchers(HttpMethod.GET, "/api/v1/learning/progress/notifications").hasAnyRole("LEARNER","ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/v1/learning/progress/heartbeat",
                        "/api/v1/learning/progress/lessons/*").hasRole("LEARNER")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/learning/progress/notifications/*/read").hasAnyRole("LEARNER","ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/v1/support/tickets", "/api/v1/support/tickets/*",
                        "/api/v1/support/tickets/*/messages").hasRole("LEARNER")
                    .requestMatchers(HttpMethod.POST, "/api/v1/support/tickets", "/api/v1/support/tickets/preview",
                        "/api/v1/support/tickets/*/messages").hasRole("LEARNER")
                    .requestMatchers(HttpMethod.GET, "/api/v1/learning/plans", "/api/v1/learning/plans/*").hasRole("LEARNER")
                    .requestMatchers(HttpMethod.POST, "/api/v1/learning/plans", "/api/v1/learning/plans/*/items",
                        "/api/v1/learning/plans/*/items/*/finish").hasRole("LEARNER")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/learning/plans/*", "/api/v1/learning/plans/*/items/*").hasRole("LEARNER")
                    .requestMatchers(HttpMethod.GET, "/api/v1/learning/cards", "/api/v1/learning/history", "/api/v1/learning/stats", "/api/v1/learning/recommendations", "/api/v1/learning/recommendations/difficulty")
                    .authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/v1/learning/notebook",
                        "/api/v1/learning/notebook/*")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/v1/learning/notebook/from-language-item/*",
                        "/api/v1/learning/notebook/*/attempts")
                    .authenticated()
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/learning/notebook/*")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/v1/learning/cards/from-suggestion/*", "/api/v1/learning/cards/*/attempts")
                    .authenticated()
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/learning/history/*", "/api/v1/learning/data",
                        "/api/v1/practice-sessions/*/audio/*")
                    .authenticated()
                    .anyRequest()
                    .denyAll())
        .addFilterAfter(new AccountStateFilter(users), SecurityContextHolderFilter.class)
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(
            l ->
                l.logoutUrl("/api/v1/auth/logout")
                    .deleteCookies("SESSION")
                    .logoutSuccessHandler((q, r, a) -> r.setStatus(204)))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (q, r, x) ->
                            ApiErrors.write(
                                r, 401, "AUTHENTICATION_REQUIRED", "Vui lòng đăng nhập."))
                    .accessDeniedHandler(
                        (q, r, x) ->
                            ApiErrors.write(
                                r,
                                403,
                                x instanceof CsrfException ? "CSRF_INVALID" : "ACCESS_DENIED",
                                x instanceof CsrfException
                                    ? "Phiên bảo vệ đã thay đổi. Vui lòng thử lại."
                                    : "Bạn không có quyền truy cập.")))
        .build();
  }
}
