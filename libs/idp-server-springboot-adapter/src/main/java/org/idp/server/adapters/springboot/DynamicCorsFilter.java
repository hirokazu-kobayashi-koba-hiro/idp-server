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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.idp.server.platform.exception.BadRequestException;
import org.idp.server.platform.exception.NotFoundException;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.organization.OrganizationNotFoundException;
import org.idp.server.platform.multi_tenancy.organization.OrganizationTenantResolverApi;
import org.idp.server.platform.multi_tenancy.tenant.*;
import org.idp.server.platform.multi_tenancy.tenant.config.CorsConfiguration;
import org.idp.server.usecases.IdpServerApplication;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class DynamicCorsFilter extends OncePerRequestFilter {

  private static final Pattern ORG_PATH_PATTERN =
      Pattern.compile("^/v1/management/organizations/([^/]+)/");

  /**
   * FAPI 2.0 SP §5.2.3.3 / OAuth 2.1 best practice: the authorization endpoint MUST NOT support
   * CORS. The endpoint is intended to be reached via top-level browser navigation (redirect / form
   * submission), not via XHR/fetch.
   *
   * <p>This pattern matches only the root path of the authorization endpoint ({@code
   * /{tenant}/v1/authorizations} for GET/POST). Subpaths such as {@code /push} (PAR), {@code
   * /{id}/view-data}, {@code /{id}/authorize}, {@code /federations/*} continue to receive CORS
   * headers because they are legitimately accessed by JavaScript clients.
   */
  private static final Pattern AUTHORIZATION_ENDPOINT_PATTERN =
      Pattern.compile("^/[^/]+/v1/authorizations/?$");

  TenantMetaDataApi tenantMetaDataApi;
  OrganizationTenantResolverApi organizationTenantResolverApi;
  LoggerWrapper log = LoggerWrapper.getLogger(DynamicCorsFilter.class);

  public DynamicCorsFilter(IdpServerApplication idpServerApplication) {
    this.tenantMetaDataApi = idpServerApplication.tenantMetadataApi();
    this.organizationTenantResolverApi = idpServerApplication.organizationTenantResolverApi();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    try {
      log.info("CORS filter: method={}, uri={}", request.getMethod(), request.getRequestURI());

      Tenant tenant = resolveTenant(request);
      CorsConfiguration corsConfiguration = tenant.corsConfiguration();
      List<String> allowOrigins = corsConfiguration.allowOrigins();
      String origin = request.getHeader("Origin") != null ? request.getHeader("Origin") : "";
      String allowOrigin =
          allowOrigins.stream()
              .filter(allow -> allow.equals(origin))
              .findFirst()
              .orElse(tenant.domain().value());
      log.debug(
          "DynamicCorsFilter tenantId: {} allow origin: {}",
          tenant.identifier().value(),
          allowOrigin);

      response.setHeader("Access-Control-Allow-Origin", allowOrigin);
      response.setHeader(
          "Access-Control-Allow-Credentials", String.valueOf(corsConfiguration.allowCredentials()));
      response.setHeader("Access-Control-Allow-Headers", corsConfiguration.allowHeaders());
      response.setHeader("Access-Control-Allow-Methods", corsConfiguration.allowMethods());

      if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
        response.setStatus(HttpServletResponse.SC_OK);
        return;
      }

      filterChain.doFilter(request, response);
    } catch (TenantNotFoundException | OrganizationNotFoundException exception) {

      log.warn(exception.getMessage(), exception);
      response.sendError(HttpServletResponse.SC_NOT_FOUND, exception.getMessage());
    } catch (BadRequestException exception) {

      log.warn(exception.getMessage(), exception);
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, exception.getMessage());
    }
  }

  /**
   * Resolves tenant for CORS configuration.
   *
   * <p>Resolution order:
   *
   * <ol>
   *   <li>Organization management APIs (/management/organizations/{orgId}/) -> ORGANIZER tenant
   *   <li>System management APIs (/management/) -> Admin tenant
   *   <li>Application APIs (/{tenantId}/) -> specified tenant
   * </ol>
   */
  private Tenant resolveTenant(HttpServletRequest request) {
    String path = request.getRequestURI();

    // 1. Organization management API: use organization's ORGANIZER tenant
    Matcher matcher = ORG_PATH_PATTERN.matcher(path);
    if (matcher.find()) {
      String orgId = matcher.group(1);
      return organizationTenantResolverApi.resolveOrganizerTenant(
          new OrganizationIdentifier(orgId));
    }

    // 2. System management API: use admin tenant
    if (path.contains("/management/")) {
      return tenantMetaDataApi.get(AdminTenantContext.getTenantIdentifier());
    }

    // 3. Application API: extract tenant from path
    TenantIdentifier tenantIdentifier = extractTenantIdentifier(request);
    return tenantMetaDataApi.get(tenantIdentifier);
  }

  private TenantIdentifier extractTenantIdentifier(HttpServletRequest request) {
    String path = request.getRequestURI();
    String[] parts = path.split("/");

    if (parts.length > 1) {
      return new TenantIdentifier(parts[1]);
    }

    throw new NotFoundException(String.format("invalid request path: %s", request.getRequestURI()));
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String uri = request.getRequestURI();
    String method = request.getMethod();
    // Skip CORS for actuator endpoints and simple /health, but allow CORS for tenant-specific
    // /v1/health
    return uri.equals("/health")
        || uri.startsWith("/actuator/")
        || uri.contains("/admin")
        || uri.contains("/backchannel")
        || uri.contains("/auth-views/")
        || isAuthorizationEndpoint(uri, method);
  }

  private boolean isAuthorizationEndpoint(String uri, String method) {
    if (!"GET".equalsIgnoreCase(method)
        && !"POST".equalsIgnoreCase(method)
        && !"OPTIONS".equalsIgnoreCase(method)) {
      return false;
    }
    return AUTHORIZATION_ENDPOINT_PATTERN.matcher(uri).matches();
  }
}
