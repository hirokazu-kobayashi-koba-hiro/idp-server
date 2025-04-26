package org.idp.server.core.adapters.datasource.identity.trustframework.application.query;

import org.idp.server.core.identity.trustframework.IdentityVerificationApplication;
import org.idp.server.core.identity.trustframework.application.IdentityVerificationApplicationIdentifier;
import org.idp.server.core.identity.trustframework.application.IdentityVerificationApplicationQueryRepository;
import org.idp.server.core.tenant.Tenant;

public class IdentityVerificationApplicationQueryDataSource
    implements IdentityVerificationApplicationQueryRepository {

  @Override
  public IdentityVerificationApplication get(
      Tenant tenant, IdentityVerificationApplicationIdentifier identifier) {

    return new IdentityVerificationApplication();
  }
}
