package org.idp.server.core.identity.verification.application;

import org.idp.server.core.identity.User;
import org.idp.server.core.identity.verification.delegation.ExternalWorkflowApplicationIdentifier;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public interface IdentityVerificationApplicationQueryRepository {

  IdentityVerificationApplication get(
      Tenant tenant, User user, IdentityVerificationApplicationIdentifier identifier);

  IdentityVerificationApplication get(
      Tenant tenant, ExternalWorkflowApplicationIdentifier identifier);

  IdentityVerificationApplications findAll(Tenant tenant, User user);

  IdentityVerificationApplications findList(
      Tenant tenant, User user, IdentityVerificationApplicationQueries queries);
}
