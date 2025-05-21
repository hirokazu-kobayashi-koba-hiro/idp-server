package org.idp.server.core.oidc.configuration;

import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface AuthorizationServerConfigurationQueryRepository {

  AuthorizationServerConfiguration get(Tenant tenant);
}
