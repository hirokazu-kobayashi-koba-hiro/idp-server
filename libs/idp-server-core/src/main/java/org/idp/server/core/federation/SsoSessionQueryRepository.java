package org.idp.server.core.federation;

import org.idp.server.core.tenant.Tenant;

public interface SsoSessionQueryRepository {

  <T> T get(Tenant tenant, SsoSessionIdentifier ssoSessionIdentifier, Class<T> clazz);
}
