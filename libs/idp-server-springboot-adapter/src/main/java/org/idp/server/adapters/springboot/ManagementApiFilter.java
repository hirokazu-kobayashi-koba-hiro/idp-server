package org.idp.server.adapters.springboot;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

import org.idp.server.core.function.OperatorAuthenticationFunction;
import org.idp.server.core.tenant.TenantService;
import org.idp.server.core.function.UserManagementFunction;
import org.idp.server.core.adapters.IdpServerApplication;
import org.idp.server.core.protcol.TokenIntrospectionApi;
import org.idp.server.core.handler.tokenintrospection.io.TokenIntrospectionRequest;
import org.idp.server.core.handler.tokenintrospection.io.TokenIntrospectionResponse;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.admin.TokenIntrospectionCreator;
import org.idp.server.adapters.springboot.operation.IdPScope;
import org.idp.server.adapters.springboot.operation.Operator;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.type.exception.UnauthorizedException;
import org.idp.server.core.type.extension.Pairs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ManagementApiFilter extends OncePerRequestFilter {

  OperatorAuthenticationFunction operatorAuthenticationFunction;
  Logger logger = LoggerFactory.getLogger(ManagementApiFilter.class);

  public ManagementApiFilter(
      IdpServerApplication idpServerApplication) {
    this.operatorAuthenticationFunction = idpServerApplication.operatorAuthenticationFunction();
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) {

    String authorization = request.getHeader("Authorization");

    try {
      Pairs<User, String> result = operatorAuthenticationFunction.authenticate(authorization);

      Operator operator =
              new Operator(
                      result.getLeft(),
                      result.getRight(),
                      List.of(
                              IdPScope.tenant_management, IdPScope.user_management, IdPScope.client_management));
      SecurityContextHolder.getContext().setAuthentication(operator);
      filterChain.doFilter(request, response);
    } catch (UnauthorizedException e) {
      response.setHeader(
              HttpHeaders.WWW_AUTHENTICATE, e.getMessage());
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

    } catch (Exception e) {

      logger.error(e.getMessage(), e);
      response.setHeader(
              HttpHeaders.WWW_AUTHENTICATE, "error=invalid_token unexpected error occurred");
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

  }

  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().contains("/management/");
  }
}
