package org.idp.server.core.authentication.repository;

import org.idp.server.core.multi_tenancy.tenant.Tenant;

public interface AuthenticationConfigurationQueryRepository {
  <T> T get(Tenant tenant, String key, Class<T> clazz);
}
