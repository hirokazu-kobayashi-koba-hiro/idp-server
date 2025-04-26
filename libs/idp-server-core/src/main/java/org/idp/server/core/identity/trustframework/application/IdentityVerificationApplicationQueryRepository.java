package org.idp.server.core.identity.trustframework.application;

import org.idp.server.core.identity.trustframework.IdentityVerificationApplication;
import org.idp.server.core.tenant.Tenant;

public interface IdentityVerificationApplicationQueryRepository {

  IdentityVerificationApplication get(
      Tenant tenant, IdentityVerificationApplicationIdentifier identifier);
}
