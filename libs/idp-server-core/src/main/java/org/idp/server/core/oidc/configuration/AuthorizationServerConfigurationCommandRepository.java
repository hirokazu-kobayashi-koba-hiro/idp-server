package org.idp.server.core.oidc.configuration;

import org.idp.server.core.multi_tenancy.tenant.Tenant;

public interface AuthorizationServerConfigurationCommandRepository {
  void register(Tenant tenant, AuthorizationServerConfiguration authorizationServerConfiguration);

  void update(Tenant tenant, AuthorizationServerConfiguration authorizationServerConfiguration);
}
