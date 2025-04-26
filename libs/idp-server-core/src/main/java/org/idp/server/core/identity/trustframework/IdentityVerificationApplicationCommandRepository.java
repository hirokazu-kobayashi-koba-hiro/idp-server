package org.idp.server.core.identity.trustframework;

import org.idp.server.core.tenant.Tenant;

public interface IdentityVerificationApplicationCommandRepository {

  void register(Tenant tenant, IdentityVerificationApplication application);

  void update(Tenant tenant, IdentityVerificationApplication application);
}
