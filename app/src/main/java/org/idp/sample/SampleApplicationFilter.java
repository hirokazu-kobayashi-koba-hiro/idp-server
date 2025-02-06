package org.idp.sample;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.idp.sample.presentation.api.Tenant;
import org.idp.server.IdpServerApplication;
import org.idp.server.api.TokenIntrospectionApi;
import org.idp.server.handler.tokenintrospection.io.TokenIntrospectionRequest;
import org.idp.server.handler.tokenintrospection.io.TokenIntrospectionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class SampleApplicationFilter extends OncePerRequestFilter {

  TokenIntrospectionApi tokenIntrospectionApi;
  Logger logger = LoggerFactory.getLogger(SampleApplicationFilter.class);

  public SampleApplicationFilter(IdpServerApplication idpServerApplication) {
    this.tokenIntrospectionApi = idpServerApplication.tokenIntrospectionApi();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String authorization = request.getHeader("Authorization");
    Tenant tenant = extractTenant(request);
    TokenIntrospectionCreator tokenIntrospectionCreator =
        new TokenIntrospectionCreator(authorization, tenant.issuer());
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
    filterChain.doFilter(request, response);
  }

  Tenant extractTenant(HttpServletRequest request) {
    String[] paths = request.getRequestURI().split("/");
    String tenantId = paths[1];
    return Tenant.of(tenantId);
  }

  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().contains("/management/");
  }
}
