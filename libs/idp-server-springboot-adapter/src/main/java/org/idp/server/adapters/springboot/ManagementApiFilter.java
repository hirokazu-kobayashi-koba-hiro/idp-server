package org.idp.server.adapters.springboot;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.idp.server.adapters.springboot.operation.IdPScope;
import org.idp.server.adapters.springboot.operation.Operator;
import org.idp.server.core.adapters.IdpServerApplication;
import org.idp.server.core.admin.OperatorAuthenticationApi;
import org.idp.server.core.oauth.identity.User;
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

  OperatorAuthenticationApi operatorAuthenticationApi;
  Logger logger = LoggerFactory.getLogger(ManagementApiFilter.class);

  public ManagementApiFilter(IdpServerApplication idpServerApplication) {
    this.operatorAuthenticationApi = idpServerApplication.operatorAuthenticationApi();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) {

    String authorization = request.getHeader("Authorization");

    try {
      Pairs<User, String> result = operatorAuthenticationApi.authenticate(authorization);

      Operator operator =
          new Operator(
              result.getLeft(),
              result.getRight(),
              List.of(
                  IdPScope.tenant_management,
                  IdPScope.user_management,
                  IdPScope.client_management));
      SecurityContextHolder.getContext().setAuthentication(operator);
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

  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().contains("/management/");
  }
}
