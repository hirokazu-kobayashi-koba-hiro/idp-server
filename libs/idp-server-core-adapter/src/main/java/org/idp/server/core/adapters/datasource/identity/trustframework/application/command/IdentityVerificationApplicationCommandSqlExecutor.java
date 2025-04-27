package org.idp.server.core.adapters.datasource.identity.trustframework.application.command;

import org.idp.server.core.identity.verification.application.IdentityVerificationApplication;
import org.idp.server.core.tenant.Tenant;

public interface IdentityVerificationApplicationCommandSqlExecutor {
  void insert(Tenant tenant, IdentityVerificationApplication application);
}
