/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.adapters.springboot;

import org.idp.server.adapters.springboot.application.restapi.model.IdPApplicationScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  ManagementApiFilter managementApiFilter;
  OrgManagementFilter orgManagementFilter;
  ProtectedResourceApiFilter protectedResourceApiFilter;
  DynamicCorsFilter dynamicCorsFilter;

  public SecurityConfig(
      ManagementApiFilter managementApiFilter,
      OrgManagementFilter orgManagementFilter,
      ProtectedResourceApiFilter protectedResourceApiFilter,
      DynamicCorsFilter dynamicCorsFilter) {
    this.managementApiFilter = managementApiFilter;
    this.orgManagementFilter = orgManagementFilter;
    this.protectedResourceApiFilter = protectedResourceApiFilter;
    this.dynamicCorsFilter = dynamicCorsFilter;
  }

  @Bean
  public SecurityFilterChain web(HttpSecurity http) throws Exception {
    http.sessionManagement(
        httpSecuritySessionManagementConfigurer ->
            httpSecuritySessionManagementConfigurer.sessionCreationPolicy(
                SessionCreationPolicy.STATELESS));
    http.csrf(AbstractHttpConfigurer::disable);
    http.httpBasic(AbstractHttpConfigurer::disable);

    http.authorizeHttpRequests(
        (authorize) ->
            authorize
                .requestMatchers(HttpMethod.POST, "/*/v1/me/identity-verification/applications/*/*")
                .hasAuthority(IdPApplicationScope.identity_verification_application.name())
                .requestMatchers(
                    HttpMethod.POST, "/*/v1/me/identity-verification/applications/*/*/*")
                .hasAuthority(IdPApplicationScope.identity_verification_application.name())
                .requestMatchers("/*/v1/me/identity-verification/applications")
                .hasAuthority(IdPApplicationScope.identity_verification_application.name())
                .requestMatchers(
                    HttpMethod.DELETE, "/*/v1/me/identity-verification/applications/*/*")
                .hasAuthority(IdPApplicationScope.identity_verification_application_delete.name())
                .requestMatchers("/*/v1/me/identity-verification/results")
                .hasAuthority(IdPApplicationScope.identity_verification_result.name())
                .anyRequest()
                .permitAll());

    // Organization-level management API filter (handles /organizations/* and /org-management/*)
    http.addFilterBefore(orgManagementFilter, BasicAuthenticationFilter.class);
    // System-level management API filter (handles /management/*)
    http.addFilterBefore(managementApiFilter, BasicAuthenticationFilter.class);
    http.addFilterBefore(protectedResourceApiFilter, ManagementApiFilter.class);
    http.addFilterBefore(dynamicCorsFilter, ProtectedResourceApiFilter.class);

    return http.build();
  }

  /**
   * idp-server authenticates through the custom filters above (token / scope based), not through
   * Spring Security's {@code UserDetailsService}. Declaring an empty one makes {@code
   * UserDetailsServiceAutoConfiguration} back off ({@code @ConditionalOnMissingBean
   * UserDetailsService}), so Spring Boot does not auto-generate a default in-memory user / random
   * password — nor its {@code "Using generated security password"} startup warning. (#1348)
   *
   * <p><b>Load-bearing</b>: do not remove. Without this bean the auto-configuration re-activates
   * and the default user and startup warning come back.
   */
  @Bean
  public UserDetailsService userDetailsService() {
    return username -> {
      throw new UsernameNotFoundException("idp-server does not use UserDetailsService-based login");
    };
  }
}
