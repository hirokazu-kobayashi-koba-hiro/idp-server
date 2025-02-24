package org.idp.sample;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.idp.sample.domain.model.authorization.AdminCredentialConvertor;
import org.idp.server.basic.http.BasicAuth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AdminApiFilter extends OncePerRequestFilter {

  String apiKey;
  String apiSecret;
  Logger logger = LoggerFactory.getLogger(AdminApiFilter.class);

  public AdminApiFilter(
      @Value("${idp.configurations.apiKey}") String apiKey,
      @Value("${idp.configurations.apiSecret}") String apiSecret) {
    this.apiKey = apiKey;
    this.apiSecret = apiSecret;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
    AdminCredentialConvertor adminCredentialConvertor =
        new AdminCredentialConvertor(authorizationHeader);
    BasicAuth basicAuth = adminCredentialConvertor.toBasicAuth();

    if (!basicAuth.exists()) {
      logger.warn("admin credential not found");
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }

    if (!basicAuth.equals(new BasicAuth(apiKey, apiSecret))) {
      logger.warn("admin credential not matched");
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }

    filterChain.doFilter(request, response);
  }

  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().contains("/admin/");
  }
}
