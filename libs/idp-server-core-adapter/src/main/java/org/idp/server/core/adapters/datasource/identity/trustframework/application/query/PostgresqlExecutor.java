package org.idp.server.core.adapters.datasource.identity.trustframework.application.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.basic.datasource.SqlExecutor;
import org.idp.server.core.identity.verification.application.IdentityVerificationApplicationIdentifier;
import org.idp.server.core.tenant.Tenant;

public class PostgresqlExecutor implements IdentityVerificationApplicationQuerySqlExecutor {

  @Override
  public Map<String, String> selectOne(
      Tenant tenant, IdentityVerificationApplicationIdentifier identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
                SELECT id,
                       tenant_id,
                       client_id,
                       user_id,
                       verification_type,
                       application_details,
                       trust_framework,
                       trust_framework_details,
                       status,
                       requested_at,
                       external_workflow_delegation,
                       external_application_id,
                       external_application_details,
                       examination_results,
                       comment
                 FROM identity_verification_applications
                 WHERE id = ?
                 AND tenant_id = ?;
                """;

    List<Object> params = new ArrayList<>();
    params.add(identifier.value());
    params.add(tenant.identifierValue());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }
}
