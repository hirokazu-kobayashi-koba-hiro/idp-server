package org.idp.server.core.authentication.repository;

import org.idp.server.core.authentication.AuthenticationConfiguration;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public interface AuthenticationConfigurationCommandRepository {
  void register(Tenant tenant, AuthenticationConfiguration configuration);
}
