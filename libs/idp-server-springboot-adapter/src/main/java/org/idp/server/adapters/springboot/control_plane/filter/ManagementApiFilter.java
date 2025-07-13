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

package org.idp.server.adapters.springboot.control_plane.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import org.idp.server.IdpServerApplication;
import org.idp.server.adapters.springboot.control_plane.model.IdpControlPlaneAuthority;
import org.idp.server.adapters.springboot.control_plane.model.OperatorPrincipal;
import org.idp.server.control_plane.base.definition.IdpControlPlaneScope;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.identity.UserAuthenticationApi;
import org.idp.server.core.oidc.token.OAuthToken;
import org.idp.server.platform.exception.UnauthorizedException;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.AdminTenantContext;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.Pairs;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ManagementApiFilter extends OncePerRequestFilter {

  UserAuthenticationApi userAuthenticationApi;
  LoggerWrapper logger = LoggerWrapper.getLogger(ManagementApiFilter.class);

  public ManagementApiFilter(IdpServerApplication idpServerApplication) {
    this.userAuthenticationApi = idpServerApplication.operatorAuthenticationApi();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) {

    String authorization = request.getHeader("Authorization");

    try {
      TenantIdentifier adminTenantIdentifier = AdminTenantContext.getTenantIdentifier();
      Pairs<User, OAuthToken> result =
          userAuthenticationApi.authenticate(adminTenantIdentifier, authorization);

      User user = result.getLeft();
      OAuthToken oAuthToken = result.getRight();

      if (oAuthToken.isClientCredentialsGrant()) {
        response.setHeader(
            HttpHeaders.WWW_AUTHENTICATE,
            "error=invalid_token, error_description=management api is not supported token type client credentials grant.");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        return;
      }

      List<IdpControlPlaneAuthority> scopes = new ArrayList<>();
      for (String scope : oAuthToken.scopeAsList()) {
        scopes.add(IdpControlPlaneAuthority.of(scope));
      }

      if (scopes.stream()
          .noneMatch(
              authority ->
                  authority.getAuthority().equals(IdpControlPlaneScope.management.name()))) {

        response.setHeader(
            HttpHeaders.WWW_AUTHENTICATE,
            "error=access_denied, error_description=scope api is not supported token type client credentials grant.");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        return;
      }

      OperatorPrincipal operatorPrincipal = new OperatorPrincipal(user, oAuthToken, scopes);
      SecurityContextHolder.getContext().setAuthentication(operatorPrincipal);
      filterChain.doFilter(request, response);
    } catch (UnauthorizedException e) {
      response.setHeader(HttpHeaders.WWW_AUTHENTICATE, e.getMessage());
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

    } catch (Exception e) {

      logger.error(e.getMessage(), e);
      response.setHeader(
          HttpHeaders.WWW_AUTHENTICATE,
          "error=invalid_token, error_description=unexpected error occurred");
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().contains("/management/");
  }
}
