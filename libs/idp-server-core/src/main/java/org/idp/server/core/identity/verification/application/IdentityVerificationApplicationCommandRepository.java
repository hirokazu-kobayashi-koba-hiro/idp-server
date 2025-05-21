package org.idp.server.core.identity.verification.application;

import org.idp.server.core.identity.User;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface IdentityVerificationApplicationCommandRepository {

  void register(Tenant tenant, IdentityVerificationApplication application);

  void update(Tenant tenant, IdentityVerificationApplication application);

  void delete(Tenant tenant, User user, IdentityVerificationApplicationIdentifier identifier);
}
