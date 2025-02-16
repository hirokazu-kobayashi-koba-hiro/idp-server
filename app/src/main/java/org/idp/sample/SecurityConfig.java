package org.idp.sample;

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
}
