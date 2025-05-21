package org.idp.server.core.authentication.repository;

import org.idp.server.core.authentication.AuthenticationConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface AuthenticationConfigurationCommandRepository {
  void register(Tenant tenant, AuthenticationConfiguration configuration);

  void update(Tenant tenant, AuthenticationConfiguration configuration);

  void delete(Tenant tenant, AuthenticationConfiguration configuration);
}
