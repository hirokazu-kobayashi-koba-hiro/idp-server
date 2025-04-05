package org.idp.server.core.mfa;

import org.idp.server.core.tenant.Tenant;

public interface MfaConfigurationQueryRepository {
  <T> T get(Tenant tenant, String key, Class<T> clazz);
}
