package org.idp.server.core.oidc.federation.sso;

import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface SsoSessionCommandRepository {

  <T> void register(Tenant tenant, SsoSessionIdentifier identifier, T payload);

  void delete(Tenant tenant, SsoSessionIdentifier identifier);
}
