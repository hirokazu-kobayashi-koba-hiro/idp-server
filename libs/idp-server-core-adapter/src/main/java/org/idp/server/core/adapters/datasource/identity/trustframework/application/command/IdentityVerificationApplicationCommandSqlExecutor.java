package org.idp.server.core.adapters.datasource.identity.trustframework.application.command;

import org.idp.server.core.identity.trustframework.IdentityVerificationApplication;
import org.idp.server.core.tenant.Tenant;

public interface IdentityVerificationApplicationCommandSqlExecutor {
  void insert(Tenant tenant, IdentityVerificationApplication application);
}
