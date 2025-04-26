package org.idp.server.core.adapters.datasource.identity.trustframework.application.query;

import org.idp.server.core.identity.trustframework.IdentityVerificationApplication;
import org.idp.server.core.identity.trustframework.IdentityVerificationApplicationIdentifier;
import org.idp.server.core.identity.trustframework.IdentityVerificationApplicationQueryRepository;
import org.idp.server.core.tenant.Tenant;

public class IdentityVerificationApplicationQueryDataSource
    implements IdentityVerificationApplicationQueryRepository {

  @Override
  public IdentityVerificationApplication get(
      Tenant tenant, IdentityVerificationApplicationIdentifier identifier) {
    return null;
  }
}
