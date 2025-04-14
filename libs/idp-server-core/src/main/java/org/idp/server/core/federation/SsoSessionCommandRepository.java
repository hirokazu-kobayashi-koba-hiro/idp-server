package org.idp.server.core.federation;

import org.idp.server.core.tenant.Tenant;

public interface SsoSessionCommandRepository {

  <T> void register(Tenant tenant, SsoSessionIdentifier identifier, T payload);

  void delete(Tenant tenant, SsoSessionIdentifier identifier);
}
