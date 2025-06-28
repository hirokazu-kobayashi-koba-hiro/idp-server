/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.adapters.springboot.application.session;

import jakarta.servlet.http.HttpServletRequest;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantAttributes;
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
    TenantAttributes attributes = tenant.attributes();
    setCookieName(attributes.optValueAsString("cookie_name", "IDP_SERVER_SESSION"));
    setDomainName(tenant.domain().host());
    setSameSite(attributes.optValueAsString("cookie_same_site", "None"));
    setUseSecureCookie(attributes.optValueAsBoolean("use_secure_cookie", true));
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
