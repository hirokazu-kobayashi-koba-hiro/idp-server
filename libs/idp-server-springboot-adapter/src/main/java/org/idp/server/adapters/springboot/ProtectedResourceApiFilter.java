package org.idp.server.adapters.springboot;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import org.idp.server.adapters.springboot.operation.IdPScope;
import org.idp.server.adapters.springboot.operation.ResourceOwnerPrincipal;
import org.idp.server.core.IdpServerApplication;
import org.idp.server.core.admin.OperatorAuthenticationApi;
import org.idp.server.core.identity.User;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.token.OAuthToken;
import org.idp.server.core.type.exception.UnSupportedException;
import org.idp.server.core.type.exception.UnauthorizedException;
import org.idp.server.core.type.extension.Pairs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ProtectedResourceApiFilter extends OncePerRequestFilter {

  OperatorAuthenticationApi operatorAuthenticationApi;
  Logger logger = LoggerFactory.getLogger(ProtectedResourceApiFilter.class);

  public ProtectedResourceApiFilter(IdpServerApplication idpServerApplication) {
    this.operatorAuthenticationApi = idpServerApplication.operatorAuthenticationApi();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) {

    String authorization = request.getHeader("Authorization");

    try {
      TenantIdentifier adminTenantIdentifier = extractTenantIdentifier(request);
      Pairs<User, OAuthToken> result =
          operatorAuthenticationApi.authenticate(adminTenantIdentifier, authorization);
      User user = result.getLeft();
      OAuthToken oAuthToken = result.getRight();

      List<IdPScope> idPScopes = new ArrayList<>();
      for (String scope : oAuthToken.scopeAsList()) {
        idPScopes.add(IdPScope.of(scope));
      }

      ResourceOwnerPrincipal principal = new ResourceOwnerPrincipal(user, oAuthToken, idPScopes);
      SecurityContextHolder.getContext().setAuthentication(principal);
      filterChain.doFilter(request, response);
    } catch (UnauthorizedException e) {
      response.setHeader(HttpHeaders.WWW_AUTHENTICATE, e.getMessage());
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

    } catch (Exception e) {

      logger.error(e.getMessage(), e);
      response.setHeader(
          HttpHeaders.WWW_AUTHENTICATE, "error=invalid_token unexpected error occurred");
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
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
    return !request.getRequestURI().contains("/identity/");
  }
}
