package org.idp.server.core.identity.trustframework;

import org.idp.server.core.tenant.Tenant;

public interface IdentityVerificationApplicationQueryRepository {

  IdentityVerificationApplication get(
      Tenant tenant, IdentityVerificationApplicationIdentifier identifier);
}
