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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.idp.server.IdpServerApplication;
import org.idp.server.adapters.springboot.control_plane.model.IdpControlPlaneAuthority;
import org.idp.server.adapters.springboot.control_plane.model.OrganizationOperatorPrincipal;
import org.idp.server.adapters.springboot.control_plane.model.OrganizationResolver;
import org.idp.server.control_plane.base.definition.IdpControlPlaneScope;
import org.idp.server.core.openid.identity.OrganizationUserAuthenticationApi;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.exception.BadRequestException;
import org.idp.server.platform.exception.UnauthorizedException;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.log.TenantLoggingContext;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.organization.OrganizationNotFoundException;
import org.idp.server.platform.type.Pairs;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Organization-level management API filter.
 *
 * <p>This filter handles authentication and authorization for organization-level management APIs.
 * It performs the following operations:
 *
 * <ol>
 *   <li>Resolves organization identifier from the request
 *   <li>Finds the admin tenant for the organization
 *   <li>Authenticates the user against the admin tenant
 *   <li>Validates required scopes and permissions
 *   <li>Sets up organization-aware security context
 * </ol>
 *
 * <p>Key features: - Organization-scoped authentication using admin tenant - Support for multiple
 * organization identifier sources (URL, header, query param) - Comprehensive error handling with
 * proper HTTP status codes - Integration with Spring Security context
 */
@Component
public class OrgManagementFilter extends OncePerRequestFilter {

  private final OrganizationUserAuthenticationApi organizationUserAuthenticationApi;
  private final LoggerWrapper logger = LoggerWrapper.getLogger(OrgManagementFilter.class);

  public OrgManagementFilter(IdpServerApplication idpServerApplication) {
    this.organizationUserAuthenticationApi =
        idpServerApplication.organizationUserAuthenticationApi();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) {

    try {
      // 1. Resolve organization identifier from request
      OrganizationIdentifier organizationId = resolveOrganizationId(request);

      // 3. Authenticate user against admin tenant
      String authorization = request.getHeader("Authorization");
      String clientCert = request.getHeader("x-ssl-cert");

      Pairs<User, OAuthToken> authResult =
          organizationUserAuthenticationApi.authenticate(organizationId, authorization, clientCert);

      User user = authResult.getLeft();
      OAuthToken oAuthToken = authResult.getRight();
      TenantLoggingContext.setTenant(oAuthToken.tenantIdentifier());
      TenantLoggingContext.setClientId(oAuthToken.requestedClientId().value());

      // 4. Validate token type (no client credentials for management API)
      if (oAuthToken.isClientCredentialsGrant()) {
        respondWithError(
            response,
            HttpServletResponse.SC_UNAUTHORIZED,
            "invalid_token",
            "organization management API does not support client credentials grant");
        return;
      }

      // 5. Validate required scopes
      List<IdpControlPlaneAuthority> authorities = extractAuthorities(oAuthToken);
      if (!hasRequiredScope(authorities)) {
        respondWithError(
            response,
            HttpServletResponse.SC_FORBIDDEN,
            "insufficient_scope",
            "required scope 'org-management' not present");
        return;
      }

      // 6. Create organization-aware principal and set security context
      OrganizationOperatorPrincipal principal =
          new OrganizationOperatorPrincipal(
              user, oAuthToken, organizationId, oAuthToken.tenantIdentifier(), authorities);
      SecurityContextHolder.getContext().setAuthentication(principal);

      logger.info(
          "Organization management authentication successful - org: {}, user: {}, tenant: {}",
          organizationId.value(),
          user.userIdentifier().value(),
          oAuthToken.tenantIdentifier().value());

      filterChain.doFilter(request, response);

    } catch (UnauthorizedException e) {
      logger.warn("Organization management authentication failed: {}", e.getMessage());
      String errorMessage = extractErrorMessage(e.getMessage());
      respondWithError(
          response, HttpServletResponse.SC_UNAUTHORIZED, "invalid_token", errorMessage);
    } catch (OrganizationNotFoundException e) {
      logger.warn("Organization management authentication failed: {}", e.getMessage());
      respondWithError(
          response, HttpServletResponse.SC_NOT_FOUND, "invalid_request", e.getMessage());
    } catch (BadRequestException e) {
      logger.warn("Organization management authentication failed: {}", e.getMessage());
      respondWithError(
          response,
          HttpServletResponse.SC_BAD_REQUEST,
          "invalid_request",
          "invalid request. unexpected parameter.");
    } catch (Exception e) {
      logger.error("Unexpected error in organization management filter", e);
      respondWithError(
          response,
          HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "server_error",
          "unexpected error occurred");
    } finally {
      TenantLoggingContext.clearAll();
    }
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !OrganizationResolver.isOrganizationRequest(request);
  }

  private OrganizationIdentifier resolveOrganizationId(HttpServletRequest request) {
    try {
      return OrganizationResolver.resolve(request);
    } catch (UnauthorizedException e) {
      throw new UnauthorizedException("organization identifier is required for this endpoint");
    }
  }

  private String extractErrorMessage(String fullErrorMessage) {
    // Extract user-friendly error message from service error
    if (fullErrorMessage.contains("error_description=")) {
      int start = fullErrorMessage.indexOf("error_description=") + "error_description=".length();
      int end = fullErrorMessage.indexOf(" - ", start);
      if (end == -1) end = fullErrorMessage.length();
      return fullErrorMessage.substring(start, end);
    }
    return "authentication failed";
  }

  private List<IdpControlPlaneAuthority> extractAuthorities(OAuthToken oAuthToken) {
    List<IdpControlPlaneAuthority> authorities = new ArrayList<>();
    for (String scope : oAuthToken.scopeAsList()) {
      authorities.add(IdpControlPlaneAuthority.of(scope));
    }
    return authorities;
  }

  private boolean hasRequiredScope(List<IdpControlPlaneAuthority> authorities) {
    return authorities.stream()
        .anyMatch(
            authority -> {
              String authorityName = authority.getAuthority();
              return authorityName.equals("org-management")
                  || authorityName.equals(IdpControlPlaneScope.management.name());
            });
  }

  private void respondWithError(
      HttpServletResponse response, int status, String error, String errorDescription) {
    try {
      // Set WWW-Authenticate header per RFC 6750
      String wwwAuthenticate =
              String.format("Bearer error=\"%s\", error_description=\"%s\"", error, errorDescription);
      response.setHeader(HttpHeaders.WWW_AUTHENTICATE, wwwAuthenticate);

      // Set status code
      response.setStatus(status);

      // Set Content-Type for JSON response body
      response.setContentType("application/json");
      response.setCharacterEncoding("UTF-8");

      // Write JSON error response body
      String jsonErrorResponse =
              String.format(
                      "{\"error\":\"%s\",\"error_description\":\"%s\"}",
                      error, errorDescription.replace("\"", "\\\""));

      response.getWriter().write(jsonErrorResponse);
      response.getWriter().flush();
    } catch (IOException ioException) {
      logger.error("Failed to write error response", ioException);
    }
  }

}
