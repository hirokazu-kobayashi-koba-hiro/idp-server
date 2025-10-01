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
import org.idp.server.adapters.springboot.application.restapi.model.IdPApplicationScope;
import org.idp.server.adapters.springboot.application.restapi.model.ResourceOwnerPrincipal;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserAuthenticationApi;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.exception.UnauthorizedException;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.Pairs;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ProtectedResourceApiFilter extends OncePerRequestFilter {

  UserAuthenticationApi userAuthenticationApi;
  LoggerWrapper logger = LoggerWrapper.getLogger(ProtectedResourceApiFilter.class);

  public ProtectedResourceApiFilter(IdpServerApplication idpServerApplication) {
    this.userAuthenticationApi = idpServerApplication.operatorAuthenticationApi();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) {

    String authorization = request.getHeader("Authorization");
    String clientCert = request.getHeader("x-ssl-cert");

    try {
      TenantIdentifier adminTenantIdentifier = extractTenantIdentifier(request);
      Pairs<User, OAuthToken> result =
          userAuthenticationApi.authenticate(adminTenantIdentifier, authorization, clientCert);
      User user = result.getLeft();
      OAuthToken oAuthToken = result.getRight();

      List<IdPApplicationScope> idPApplicationScopes = new ArrayList<>();
      for (String scope : oAuthToken.scopeAsList()) {
        idPApplicationScopes.add(IdPApplicationScope.of(scope));
      }

      ResourceOwnerPrincipal principal =
          new ResourceOwnerPrincipal(user, oAuthToken, idPApplicationScopes);
      SecurityContextHolder.getContext().setAuthentication(principal);
      filterChain.doFilter(request, response);
    } catch (UnauthorizedException e) {
      writeErrorResponse(response, "invalid_token", e.getMessage());

    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      writeErrorResponse(response, "invalid_token", "Unexpected error occurred");
    }
  }

  private void writeErrorResponse(
      HttpServletResponse response, String error, String errorDescription) {
    try {
      // Set WWW-Authenticate header per RFC 6750
      String wwwAuthenticate =
          String.format("Bearer error=\"%s\", error_description=\"%s\"", error, errorDescription);
      response.setHeader(HttpHeaders.WWW_AUTHENTICATE, wwwAuthenticate);

      // Set status code
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

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

  private TenantIdentifier extractTenantIdentifier(HttpServletRequest request) {
    String path = request.getRequestURI();
    String[] parts = path.split("/");

    if (parts.length > 1) {
      return new TenantIdentifier(parts[1]);
    }

    throw new UnSupportedException("invalid request path");
  }

  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    List<String> protectedPrefixes = List.of("/me");
    List<String> excludedPrefixes = List.of("/admin/", "/management/");
    boolean protectedResourceMatch = protectedPrefixes.stream().anyMatch(path::contains);
    boolean excludedResourceMatch = excludedPrefixes.stream().anyMatch(path::contains);
    return !protectedResourceMatch || excludedResourceMatch;
  }
}
