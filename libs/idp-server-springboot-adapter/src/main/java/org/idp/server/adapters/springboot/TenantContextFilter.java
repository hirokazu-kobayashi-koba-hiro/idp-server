package org.idp.server.adapters.springboot;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.idp.server.core.tenant.TenantContext;
import org.idp.server.core.tenant.TenantIdentifier;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TenantContextFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String path = request.getRequestURI(); // 例: /acme/api/v1/authorizations
    String[] segments = path.split("/");
    if (segments.length > 1) {
      String tenantId = segments[1]; // ← パスの先頭をテナントIDとみなす
      TenantContext.set(new TenantIdentifier(tenantId));
    }

    try {
      filterChain.doFilter(request, response);
    } finally {
      TenantContext.clear();
    }
  }

  protected boolean shouldNotFilter(HttpServletRequest request) {
    return request.getRequestURI().contains("/admin/")
        || request.getRequestURI().contains("/management/");
  }
}
