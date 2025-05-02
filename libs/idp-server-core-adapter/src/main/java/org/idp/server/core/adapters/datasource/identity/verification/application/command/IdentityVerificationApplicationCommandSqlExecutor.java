package org.idp.server.core.adapters.datasource.identity.verification.application.command;

import org.idp.server.core.identity.User;
import org.idp.server.core.identity.verification.application.IdentityVerificationApplication;
import org.idp.server.core.identity.verification.application.IdentityVerificationApplicationIdentifier;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public interface IdentityVerificationApplicationCommandSqlExecutor {
  void insert(Tenant tenant, IdentityVerificationApplication application);

  void update(Tenant tenant, IdentityVerificationApplication application);

  void delete(Tenant tenant, User user, IdentityVerificationApplicationIdentifier identifier);
}
