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

package org.idp.server.adapters.springboot.application.config;

import org.idp.server.IdpServerApplication;
import org.idp.server.adapters.springboot.application.filter.DynamicCorsFilter;
import org.idp.server.adapters.springboot.application.filter.ProtectedResourceApiFilter;
import org.idp.server.adapters.springboot.application.restapi.model.IdPApplicationScope;
import org.idp.server.adapters.springboot.application.session.DynamicCookieSerializer;
import org.idp.server.adapters.springboot.control_plane.filter.ManagementApiFilter;
import org.idp.server.platform.multi_tenancy.tenant.TenantMetaDataApi;
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

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  ManagementApiFilter managementApiFilter;
  ProtectedResourceApiFilter protectedResourceApiFilter;
  DynamicCorsFilter dynamicCorsFilter;
  TenantMetaDataApi tenantMetaDataApi;

  public SecurityConfig(
      ManagementApiFilter managementApiFilter,
      ProtectedResourceApiFilter protectedResourceApiFilter,
      DynamicCorsFilter dynamicCorsFilter,
      IdpServerApplication idpServerApplication) {
    this.managementApiFilter = managementApiFilter;
    this.protectedResourceApiFilter = protectedResourceApiFilter;
    this.dynamicCorsFilter = dynamicCorsFilter;
    this.tenantMetaDataApi = idpServerApplication.tenantMetadataApi();
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
                    "/{tenant-id}/v1/identity/{verification-type}/{verification-process}")
                .hasAuthority(IdPApplicationScope.identity_verification_application.name())
                .anyRequest()
                .permitAll());

    http.addFilterBefore(managementApiFilter, BasicAuthenticationFilter.class);
    http.addFilterBefore(protectedResourceApiFilter, ManagementApiFilter.class);
    http.addFilterBefore(dynamicCorsFilter, ProtectedResourceApiFilter.class);

    return http.build();
  }

  @Bean
  public CookieSerializer cookieSerializer() {
    return new DynamicCookieSerializer(tenantMetaDataApi);
  }
}
