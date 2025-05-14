package org.idp.server.core.federation.sso;

import org.idp.server.core.multi_tenancy.tenant.Tenant;

public interface SsoSessionCommandRepository {

  <T> void register(Tenant tenant, SsoSessionIdentifier identifier, T payload);

  void delete(Tenant tenant, SsoSessionIdentifier identifier);
}
