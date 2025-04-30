package org.idp.server.core.adapters.datasource.identity.verification.application.query;

import java.util.List;
import java.util.Map;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.verification.application.IdentityVerificationApplicationIdentifier;
import org.idp.server.core.identity.verification.application.IdentityVerificationApplicationQueries;
import org.idp.server.core.identity.verification.delegation.ExternalWorkflowApplicationIdentifier;
import org.idp.server.core.tenant.Tenant;

public interface IdentityVerificationApplicationQuerySqlExecutor {
  Map<String, String> selectOne(
      Tenant tenant, IdentityVerificationApplicationIdentifier identifier);

  Map<String, String> selectOne(Tenant tenant, ExternalWorkflowApplicationIdentifier identifier);

  List<Map<String, String>> selectList(Tenant tenant, User user);

  List<Map<String, String>> selectList(
      Tenant tenant, User user, IdentityVerificationApplicationQueries queries);
}
