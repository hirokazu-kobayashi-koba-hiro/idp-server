package org.idp.server.core.federation;

import org.idp.server.core.tenant.Tenant;

public interface FederationConfigurationQueryRepository {

  <T> T get(Tenant tenant, FederationType federationType, SsoProvider ssoProvider, Class<T> clazz);
}
