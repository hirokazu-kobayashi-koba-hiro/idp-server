package org.idp.server.adapters.springboot;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.idp.server.adapters.springboot.operation.IdPScope;
import org.idp.server.adapters.springboot.operation.Operator;
import org.idp.server.basic.exception.UnauthorizedException;
import org.idp.server.basic.log.LoggerWrapper;
import org.idp.server.basic.type.extension.Pairs;
import org.idp.server.core.admin.OperatorAuthenticationApi;
import org.idp.server.core.identity.User;
import org.idp.server.core.multi_tenancy.tenant.AdminTenantContext;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.token.OAuthToken;
import org.idp.server.usecases.IdpServerApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ManagementApiFilter extends OncePerRequestFilter {

  OperatorAuthenticationApi operatorAuthenticationApi;
  LoggerWrapper logger = LoggerWrapper.getLogger(ManagementApiFilter.class);

  public ManagementApiFilter(IdpServerApplication idpServerApplication) {
    this.operatorAuthenticationApi = idpServerApplication.operatorAuthenticationApi();
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) {

    String authorization = request.getHeader("Authorization");

    try {
      TenantIdentifier adminTenantIdentifier = AdminTenantContext.getTenantIdentifier();
      Pairs<User, OAuthToken> result = operatorAuthenticationApi.authenticate(adminTenantIdentifier, authorization);

      Operator operator = new Operator(result.getLeft(), result.getRight(), List.of(IdPScope.tenant_management, IdPScope.user_management, IdPScope.client_management));
      SecurityContextHolder.getContext().setAuthentication(operator);
      filterChain.doFilter(request, response);
    } catch (UnauthorizedException e) {
      response.setHeader(HttpHeaders.WWW_AUTHENTICATE, e.getMessage());
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

    } catch (Exception e) {

      logger.error(e.getMessage(), e);
      response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "error=invalid_token unexpected error occurred");
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }
  }

  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().contains("/management/");
  }
}
