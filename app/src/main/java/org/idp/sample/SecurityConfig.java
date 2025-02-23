package org.idp.sample;

import java.util.List;
import org.idp.sample.domain.model.operation.IdPScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  ManagementApiFilter managementApiFilter;

  public SecurityConfig(ManagementApiFilter managementApiFilter) {
    this.managementApiFilter = managementApiFilter;
  }

  @Bean
  public SecurityFilterChain web(HttpSecurity http) throws Exception {
    http.sessionManagement(
        httpSecuritySessionManagementConfigurer ->
            httpSecuritySessionManagementConfigurer.sessionCreationPolicy(
                SessionCreationPolicy.STATELESS));
    http.csrf(AbstractHttpConfigurer::disable);

    http.cors((cors) -> cors.configurationSource(corsConfigurationSource()));

    http.authorizeHttpRequests(
        (authorize) ->
            authorize
                .requestMatchers(
                    "/{tenant-id}/api/v1/management/management/tenants",
                    "/{tenant-id}/api/v1/management/management/tenants/**")
                .hasAuthority(IdPScope.tenant_management.name())
                .requestMatchers(
                    "/{tenant-id}/api/v1/management/management/clients",
                    "/{tenant-id}/api/v1/management/management/clients/**")
                .hasAuthority(IdPScope.client_management.name())
                .requestMatchers(
                    "/{tenant-id}/api/v1/management/management/users",
                    "/{tenant-id}/api/v1/management/management/users/**")
                .hasAuthority(IdPScope.user_management.name())
                .anyRequest()
                .permitAll());

    http.addFilterBefore(managementApiFilter, BasicAuthenticationFilter.class);

    return http.build();
  }

  public UrlBasedCorsConfigurationSource corsConfigurationSource() {
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of("http://localhost:3100"));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true);
    source.registerCorsConfiguration("/**", config);
    return source;
  }

  @Bean
  public CookieSerializer cookieSerializer() {
    DefaultCookieSerializer serializer = new DefaultCookieSerializer();
    serializer.setCookieName("IDP_SERVER_SESSION");
    serializer.setCookiePath("/");
    serializer.setDomainName("localhost");
    serializer.setUseSecureCookie(false);
    serializer.setSameSite("Lax");
    serializer.setUseHttpOnlyCookie(true);
    return serializer;
  }
}
