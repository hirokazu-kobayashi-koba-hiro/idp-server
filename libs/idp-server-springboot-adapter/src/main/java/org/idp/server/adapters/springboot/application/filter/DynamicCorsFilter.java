package org.idp.server.adapters.springboot.application.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.idp.server.IdpServerApplication;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.TenantDomain;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.multi_tenancy.tenant.TenantMetaDataApi;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.log.LoggerWrapper;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class DynamicCorsFilter extends OncePerRequestFilter {
  TenantMetaDataApi tenantMetaDataApi;
  LoggerWrapper log = LoggerWrapper.getLogger(DynamicCorsFilter.class);

  public DynamicCorsFilter(IdpServerApplication idpServerApplication) {
    this.tenantMetaDataApi = idpServerApplication.tenantMetadataApi();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    TenantIdentifier tenantIdentifier = extractTenantIdentifier(request);
    Tenant tenant = tenantMetaDataApi.get(tenantIdentifier);
    TenantDomain domain = tenant.domain();
    log.info("DynamicCorsFilter tenantId: {} domain: {}", tenantIdentifier.value(), domain.host());

    response.setHeader("Access-Control-Allow-Origin", domain.host());
    response.setHeader("Access-Control-Allow-Credentials", "true");
    response.setHeader(
        "Access-Control-Allow-Headers", "Authorization, Content-Type, Accept, X-Requested-With");
    response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");

    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
      response.setStatus(HttpServletResponse.SC_OK);
      return;
    }

    filterChain.doFilter(request, response);
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
    return true;
    //    return path.contains("/admin/") || path.contains("/management/");
  }
}
