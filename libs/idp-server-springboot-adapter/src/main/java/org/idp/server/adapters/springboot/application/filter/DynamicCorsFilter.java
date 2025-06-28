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

package org.idp.server.adapters.springboot.application.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import org.idp.server.IdpServerApplication;
import org.idp.server.platform.exception.BadRequestException;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class DynamicCorsFilter extends OncePerRequestFilter {
  TenantMetaDataApi tenantMetaDataApi;
  URI serverUri;
  LoggerWrapper log = LoggerWrapper.getLogger(DynamicCorsFilter.class);

  public DynamicCorsFilter(
      IdpServerApplication idpServerApplication,
      @Value("${idp.configurations.serverUrl}") String serverUrl) {
    this.tenantMetaDataApi = idpServerApplication.tenantMetadataApi();
    this.serverUri = URI.create(serverUrl);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    try {
      TenantIdentifier tenantIdentifier = extractTenantIdentifier(request);
      Tenant tenant = tenantMetaDataApi.get(tenantIdentifier);
      TenantAttributes tenantAttributes = tenant.attributes();
      List<String> allowOrigins = tenantAttributes.optValueAsStringList("allow_origins", List.of());
      String origin = request.getServerName();
      String allowOrigin =
          allowOrigins.stream().filter(origin::equals).findFirst().orElse(tenant.domain().host());
      log.info(
          "DynamicCorsFilter tenantId: {} allow origin: {}", tenantIdentifier.value(), allowOrigin);

      response.setHeader("Access-Control-Allow-Origin", allowOrigin);
      response.setHeader("Access-Control-Allow-Credentials", "true");
      response.setHeader(
          "Access-Control-Allow-Headers", "Authorization, Content-Type, Accept, X-Requested-With");
      response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");

      if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
        response.setStatus(HttpServletResponse.SC_OK);
        return;
      }

      filterChain.doFilter(request, response);
    } catch (TenantNotFoundException exception) {

      log.warn(exception.getMessage(), exception);
      response.sendError(HttpServletResponse.SC_NOT_FOUND, exception.getMessage());
    } catch (BadRequestException exception) {

      log.warn(exception.getMessage(), exception);
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, exception.getMessage());
    }
  }

  private TenantIdentifier extractTenantIdentifier(HttpServletRequest request) {
    String path = request.getRequestURI();
    String[] parts = path.split("/");

    if (parts.length > 1) {
      return new TenantIdentifier(parts[1]);
    }

    throw new UnSupportedException("invalid request path");
  }

  protected boolean shouldNotFilter(HttpServletRequest request) {
    if (serverUri.getHost().equals(request.getServerName())) {
      return true;
    }
    String path = request.getRequestURI();

    return path.contains("/admin/") || path.contains("/management/");
  }
}
