package org.idp.server.core.adapters.datasource.federation.session.command;

import org.idp.server.core.oidc.federation.sso.SsoSessionIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface SsoSessionCommandSqlExecutor {

  <T> void insert(Tenant tenant, SsoSessionIdentifier identifier, T payload);

  void delete(Tenant tenant, SsoSessionIdentifier identifier);
}
