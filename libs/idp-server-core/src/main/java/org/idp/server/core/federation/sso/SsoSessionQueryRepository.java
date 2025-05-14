package org.idp.server.core.federation.sso;

import org.idp.server.core.multi_tenancy.tenant.Tenant;

public interface SsoSessionQueryRepository {

  <T> T get(Tenant tenant, SsoSessionIdentifier ssoSessionIdentifier, Class<T> clazz);
}
