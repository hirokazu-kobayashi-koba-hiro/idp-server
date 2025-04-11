package org.idp.server.core.federation;

import org.idp.server.core.tenant.TenantIdentifier;

public interface FederationConfigurationQueryRepository {

  <T> T get(
      TenantIdentifier tenantIdentifier,
      FederationType federationType,
      SsoProvider ssoProvider,
      Class<T> clazz);
}
