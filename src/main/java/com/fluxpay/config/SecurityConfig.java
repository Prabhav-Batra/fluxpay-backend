package com.fluxpay.config;

import com.fluxpay.common.config.FluxpayProperties;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    static final List<String> SESSION_PREFIXES = List.of("/api/v1/auth/", "/api/v1/dashboard/", "/api/v1/admin/");

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityContextRepository securityContextRepository,
            RestAuthenticationEntryPoint entryPoint,
            RestAccessDeniedHandler accessDeniedHandler)
            throws Exception {
        RequestMatcher notSessionRoute = request -> SESSION_PREFIXES.stream()
                .noneMatch(prefix -> request.getRequestURI().startsWith(prefix));

        http.csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers(notSessionRoute))
                .cors(Customizer.withDefaults())
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(handling ->
                        handling.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth.requestMatchers("/health/**", "/error")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/signup")
                        .permitAll()
                        .requestMatchers("/api/v1/public/**")
                        .permitAll()
                        .requestMatchers("/api/v1/auth/**")
                        .hasAnyRole("MERCHANT_OWNER", "PLATFORM_ADMIN")
                        .requestMatchers("/api/v1/dashboard/**")
                        .hasRole("MERCHANT_OWNER")
                        .requestMatchers("/api/v1/admin/**")
                        .hasRole("PLATFORM_ADMIN")
                        .requestMatchers("/api/v1/**")
                        .hasRole("API_KEY")
                        .anyRequest()
                        .denyAll());
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(FluxpayProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(properties.frontendBaseUrl()));
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE"));
        config.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN", "FluxPay-Mode", "X-Request-Id"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/**", config);
        return source;
    }
}
