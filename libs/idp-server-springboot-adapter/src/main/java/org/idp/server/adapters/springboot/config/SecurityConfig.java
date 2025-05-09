package org.idp.server.adapters.springboot.config;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.idp.server.IdpServerApplication;
import org.idp.server.adapters.springboot.filter.DynamicCorsFilter;
import org.idp.server.adapters.springboot.filter.ManagementApiFilter;
import org.idp.server.adapters.springboot.filter.ProtectedResourceApiFilter;
import org.idp.server.adapters.springboot.restapi.model.IdPScope;
import org.idp.server.adapters.springboot.session.DynamicCookieSerializer;
import org.idp.server.core.multi_tenancy.tenant.TenantMetaDataApi;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  ManagementApiFilter managementApiFilter;
  ProtectedResourceApiFilter protectedResourceApiFilter;
  DynamicCorsFilter dynamicCorsFilter;
  TenantMetaDataApi tenantMetaDataApi;
  String adminAuthViewUrl;
  String authViewUrl;
  String serverUrl;
  List<String> additionalAuthViewUrls;

  public SecurityConfig(
      ManagementApiFilter managementApiFilter,
      ProtectedResourceApiFilter protectedResourceApiFilter,
      DynamicCorsFilter dynamicCorsFilter,
      IdpServerApplication idpServerApplication,
      @Value("${idp.configurations.adminAuthViewUrl}") String adminAuthViewUrl,
      @Value("${idp.configurations.authViewUrl}") String authViewUrl,
      @Value("${idp.configurations.serverUrl}") String serverUrl,
      @Value("${idp.configurations.additionalAuthViewUrls}") String additionalAuthViewUrls) {
    this.managementApiFilter = managementApiFilter;
    this.protectedResourceApiFilter = protectedResourceApiFilter;
    this.dynamicCorsFilter = dynamicCorsFilter;
    this.tenantMetaDataApi = idpServerApplication.tenantMetadataApi();
    this.adminAuthViewUrl = adminAuthViewUrl;
    this.authViewUrl = authViewUrl;
    this.serverUrl = serverUrl;
    this.additionalAuthViewUrls = transformAdditionalAuthViewUrls(additionalAuthViewUrls);
  }

  @Bean
  public SecurityFilterChain web(HttpSecurity http) throws Exception {
    http.sessionManagement(
        httpSecuritySessionManagementConfigurer ->
            httpSecuritySessionManagementConfigurer.sessionCreationPolicy(
                SessionCreationPolicy.STATELESS));
    http.csrf(AbstractHttpConfigurer::disable);

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
                .requestMatchers(
                    "/{tenant-id}/api/v1/identity/{verification-type}/{verification-process}")
                .hasAuthority(IdPScope.identity_verification_application.name())
                .anyRequest()
                .permitAll());

    http.addFilterBefore(managementApiFilter, BasicAuthenticationFilter.class);
    http.addFilterBefore(protectedResourceApiFilter, ManagementApiFilter.class);
    http.addFilterBefore(dynamicCorsFilter, ProtectedResourceApiFilter.class);

    return http.build();
  }

  public UrlBasedCorsConfigurationSource corsConfigurationSource() {
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    CorsConfiguration config = new CorsConfiguration();

    List<String> allowedOrigins = new ArrayList<>(additionalAuthViewUrls);
    allowedOrigins.add(adminAuthViewUrl);
    allowedOrigins.add(authViewUrl);
    config.setAllowedOrigins(allowedOrigins);

    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true);
    source.registerCorsConfiguration("/**", config);
    return source;
  }

  @Bean
  public CookieSerializer cookieSerializer() {
    return new DynamicCookieSerializer(tenantMetaDataApi);
  }

  private String serverDomain() {
    URI uri = URI.create(serverUrl);
    return uri.getHost();
  }

  private List<String> transformAdditionalAuthViewUrls(String additionalAuthViewUrls) {
    if (additionalAuthViewUrls == null || additionalAuthViewUrls.isEmpty()) {
      return List.of();
    }
    return Arrays.stream(additionalAuthViewUrls.split(",")).toList();
  }
}
