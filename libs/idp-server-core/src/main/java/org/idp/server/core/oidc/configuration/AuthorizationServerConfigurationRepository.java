package org.idp.server.core.oidc.configuration;

import org.idp.server.core.multi_tenancy.tenant.Tenant;

public interface AuthorizationServerConfigurationRepository {
  void register(Tenant tenant, AuthorizationServerConfiguration authorizationServerConfiguration);

  AuthorizationServerConfiguration get(Tenant tenant);
}
