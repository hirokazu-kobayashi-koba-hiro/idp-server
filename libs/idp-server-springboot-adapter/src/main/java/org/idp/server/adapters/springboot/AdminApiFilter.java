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

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.idp.server.control_plane.base.AdminCredentialConvertor;
import org.idp.server.platform.http.BasicAuth;
import org.idp.server.platform.log.LoggerWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AdminApiFilter extends OncePerRequestFilter {

  String apiKey;
  String apiSecret;
  LoggerWrapper logger = LoggerWrapper.getLogger(AdminApiFilter.class);

  public AdminApiFilter(
      @Value("${idp.configurations.apiKey}") String apiKey,
      @Value("${idp.configurations.apiSecret}") String apiSecret) {
    this.apiKey = apiKey;
    this.apiSecret = apiSecret;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
    AdminCredentialConvertor adminCredentialConvertor =
        new AdminCredentialConvertor(authorizationHeader);
    BasicAuth basicAuth = adminCredentialConvertor.toBasicAuth();

    if (!basicAuth.exists()) {
      logger.warn("admin credential not found");
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }

    if (!basicAuth.equals(new BasicAuth(apiKey, apiSecret))) {
      logger.warn("admin credential not matched");
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }

    filterChain.doFilter(request, response);
  }

  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().contains("/admin/");
  }
}
