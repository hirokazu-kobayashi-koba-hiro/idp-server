/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.adapters.springboot.application.session;

import jakarta.servlet.http.HttpServletRequest;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantMetaDataApi;
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
    // TODO dynamic setting
    setSameSite("None");
    // TODO local is false but production is true
    setUseSecureCookie(false);
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
