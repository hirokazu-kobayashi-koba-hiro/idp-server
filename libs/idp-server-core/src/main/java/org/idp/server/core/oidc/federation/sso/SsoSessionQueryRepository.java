package org.idp.server.core.oidc.federation.sso;

import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface SsoSessionQueryRepository {

  <T> T get(Tenant tenant, SsoSessionIdentifier ssoSessionIdentifier, Class<T> clazz);
}
