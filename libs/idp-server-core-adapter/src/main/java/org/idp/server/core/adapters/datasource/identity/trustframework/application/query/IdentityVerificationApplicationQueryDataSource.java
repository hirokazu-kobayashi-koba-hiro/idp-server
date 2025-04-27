package org.idp.server.core.adapters.datasource.identity.trustframework.application.query;

import org.idp.server.core.identity.User;
import org.idp.server.core.identity.verification.application.IdentityVerificationApplication;
import org.idp.server.core.identity.verification.application.IdentityVerificationApplicationIdentifier;
import org.idp.server.core.identity.verification.application.IdentityVerificationApplicationQueryRepository;
import org.idp.server.core.identity.verification.application.IdentityVerificationApplications;
import org.idp.server.core.tenant.Tenant;

public class IdentityVerificationApplicationQueryDataSource
    implements IdentityVerificationApplicationQueryRepository {

  @Override
  public IdentityVerificationApplication get(
      Tenant tenant, IdentityVerificationApplicationIdentifier identifier) {

    return new IdentityVerificationApplication();
  }

  @Override
  public IdentityVerificationApplications getAll(Tenant tenant, User user) {

    return new IdentityVerificationApplications();
  }
}
