package vn.englishconversation.config;

import java.util.List;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
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
@Profile("!bootstrap")
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
      UserRepository users)
      throws Exception {
    return http.securityContext(
            c -> c.securityContextRepository(contexts).requireExplicitSave(true))
        .csrf(c -> c.csrfTokenRepository(csrf))
        .requestCache(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(HttpMethod.GET, "/api/v1/system/health", "/api/v1/auth/csrf")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login")
                    .permitAll()
                    .requestMatchers("/api/v1/admin/**")
                    .hasRole("ADMIN")
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/topics",
                        "/api/v1/scenarios",
                        "/api/v1/scenarios/*")
                    .authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/v1/profile/me")
                    .authenticated()
                    .requestMatchers(HttpMethod.PUT, "/api/v1/profile/me")
                    .authenticated()
                    .requestMatchers("/api/v1/auth/me", "/api/v1/auth/password")
                    .authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/v1/practice-sessions/*",
                        "/api/v1/practice-sessions/*/feedback", "/api/v1/practice-sessions/*/audio/*")
                    .authenticated()
                    .requestMatchers(HttpMethod.PATCH, "/api/v1/practice-sessions/*/mode")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/v1/practice-sessions",
                        "/api/v1/practice-sessions/*/turns", "/api/v1/practice-sessions/*/hints",
                        "/api/v1/practice-sessions/*/finish", "/api/v1/practice-sessions/*/operations/*/retry",
                        "/api/v1/practice-sessions/*/operations/*/skip",
                        "/api/v1/practice-sessions/*/feedback", "/api/v1/practice-sessions/*/feedback/retry",
                        "/api/v1/practice-sessions/*/audio", "/api/v1/practice-sessions/*/audio/*/confirm")
                    .authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/v1/learning/cards", "/api/v1/learning/history", "/api/v1/learning/stats")
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
