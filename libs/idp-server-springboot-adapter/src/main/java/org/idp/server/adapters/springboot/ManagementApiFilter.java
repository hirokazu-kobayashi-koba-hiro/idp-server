package org.idp.server.adapters.springboot;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.idp.server.adapters.springboot.application.service.tenant.TenantService;
import org.idp.server.adapters.springboot.application.service.user.internal.UserService;
import org.idp.server.core.IdpServerApplication;
import org.idp.server.core.api.TokenIntrospectionApi;
import org.idp.server.core.handler.tokenintrospection.io.TokenIntrospectionRequest;
import org.idp.server.core.handler.tokenintrospection.io.TokenIntrospectionResponse;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.adapters.springboot.domain.model.authorization.TokenIntrospectionCreator;
import org.idp.server.adapters.springboot.domain.model.operation.IdPScope;
import org.idp.server.adapters.springboot.domain.model.operation.Operator;
import org.idp.server.adapters.springboot.domain.model.tenant.Tenant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ManagementApiFilter extends OncePerRequestFilter {

  TokenIntrospectionApi tokenIntrospectionApi;
  TenantService tenantService;
  UserService userService;
  Logger logger = LoggerFactory.getLogger(ManagementApiFilter.class);

  public ManagementApiFilter(
      IdpServerApplication idpServerApplication,
      TenantService tenantService,
      UserService userService) {
    this.tokenIntrospectionApi = idpServerApplication.tokenIntrospectionApi();
    this.tenantService = tenantService;
    this.userService = userService;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String authorization = request.getHeader("Authorization");
    Tenant adminTenant = tenantService.getAdmin();

    TokenIntrospectionCreator tokenIntrospectionCreator =
        new TokenIntrospectionCreator(authorization, adminTenant.issuer());
    TokenIntrospectionRequest tokenIntrospectionRequest = tokenIntrospectionCreator.create();

    if (!tokenIntrospectionRequest.hasToken()) {
      response.setHeader(
          HttpHeaders.WWW_AUTHENTICATE, "error=invalid_token error_description=token is undefined");
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }

    TokenIntrospectionResponse introspectionResponse =
        tokenIntrospectionApi.inspect(tokenIntrospectionRequest);
    logger.info(introspectionResponse.response().toString());
    if (!introspectionResponse.isActive()) {
      response.setHeader(
          HttpHeaders.WWW_AUTHENTICATE,
          "error=invalid_token error_description=token is not active");
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }
    User user = userService.get(introspectionResponse.subject());
    Operator operator =
        new Operator(
            user,
            tokenIntrospectionRequest.token(),
            List.of(
                IdPScope.tenant_management, IdPScope.user_management, IdPScope.client_management));
    SecurityContextHolder.getContext().setAuthentication(operator);
    filterChain.doFilter(request, response);
  }

  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().contains("/management/");
  }
}
