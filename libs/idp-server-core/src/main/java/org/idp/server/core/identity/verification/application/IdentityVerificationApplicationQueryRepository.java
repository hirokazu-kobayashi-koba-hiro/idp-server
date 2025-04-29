package org.idp.server.core.identity.verification.application;

import org.idp.server.core.identity.User;
import org.idp.server.core.tenant.Tenant;

public interface IdentityVerificationApplicationQueryRepository {

  IdentityVerificationApplication get(
      Tenant tenant, IdentityVerificationApplicationIdentifier identifier);

  IdentityVerificationApplications findAll(Tenant tenant, User user);

  IdentityVerificationApplications findList(
      Tenant tenant, User user, IdentityVerificationApplicationQueries queries);
}
