package org.idp.server.adapters.springboot.session;

import jakarta.servlet.http.HttpServletRequest;
import org.idp.server.basic.exception.UnSupportedException;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.multi_tenancy.tenant.TenantMetaDataApi;
import org.springframework.session.web.http.DefaultCookieSerializer;

public class DynamicCookieSerializer extends DefaultCookieSerializer {

  TenantMetaDataApi tenantMetaDataApi;

  public DynamicCookieSerializer(TenantMetaDataApi tenantMetaDataApi) {
    this.tenantMetaDataApi = tenantMetaDataApi;
  }

  @Override
  public void writeCookieValue(CookieValue cookieValue) {
    HttpServletRequest request = cookieValue.getRequest();
    TenantIdentifier tenantIdentifier = extractTenantIdentifier(request);
    Tenant tenant = tenantMetaDataApi.get(tenantIdentifier);
    setCookieName("IDP_SERVER_SESSION");
    setDomainName(tenant.domain().host());
    setSameSite("None");
    setUseSecureCookie(true);
    setUseHttpOnlyCookie(true);
    setCookiePath("/");
    super.writeCookieValue(cookieValue);
  }

  private TenantIdentifier extractTenantIdentifier(HttpServletRequest request) {
    String path = request.getRequestURI();
    String[] parts = path.split("/");

    if (parts.length > 1) {
      return new TenantIdentifier(parts[1]);
    }

    throw new UnSupportedException("invalid request path");
  }
}
