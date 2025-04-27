package org.idp.server.core.identity.verification.application;

import org.idp.server.core.identity.User;
import org.idp.server.core.tenant.Tenant;

public interface IdentityVerificationApplicationQueryRepository {

  IdentityVerificationApplication get(
      Tenant tenant, IdentityVerificationApplicationIdentifier identifier);

  IdentityVerificationApplications getAll(Tenant tenant, User user);
}
