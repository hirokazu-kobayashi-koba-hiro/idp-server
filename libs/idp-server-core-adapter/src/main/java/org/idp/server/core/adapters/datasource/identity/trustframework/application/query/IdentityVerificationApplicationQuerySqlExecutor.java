package org.idp.server.core.adapters.datasource.identity.trustframework.application.query;

import java.util.Map;
import org.idp.server.core.identity.verification.application.IdentityVerificationApplicationIdentifier;
import org.idp.server.core.tenant.Tenant;

public interface IdentityVerificationApplicationQuerySqlExecutor {
  Map<String, String> selectOne(
      Tenant tenant, IdentityVerificationApplicationIdentifier identifier);
}
