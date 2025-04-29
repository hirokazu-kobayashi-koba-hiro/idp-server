package org.idp.server.core.adapters.datasource.identity.trustframework.application.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.basic.datasource.SqlExecutor;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.verification.application.IdentityVerificationApplicationIdentifier;
import org.idp.server.core.tenant.Tenant;

public class PostgresqlExecutor implements IdentityVerificationApplicationQuerySqlExecutor {

  @Override
  public Map<String, String> selectOne(
      Tenant tenant, IdentityVerificationApplicationIdentifier identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        selectSql
            + " "
            + """
                 WHERE id = ?
                 AND tenant_id = ?;
                """;

    List<Object> params = new ArrayList<>();
    params.add(identifier.value());
    params.add(tenant.identifierValue());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public List<Map<String, String>> selectList(Tenant tenant, User user) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        selectSql
            + " "
            + """
                     WHERE user_id = ?
                     AND tenant_id = ?;
                    """;

    List<Object> params = new ArrayList<>();
    params.add(user.sub());
    params.add(tenant.identifierValue());

    return sqlExecutor.selectList(sqlTemplate, params);
  }

  String selectSql =
      """
          SELECT id,
                       tenant_id,
                       client_id,
                       user_id,
                       verification_type,
                       application_details,
                       external_workflow_delegation,
                       external_application_id,
                       external_application_details,
                       trust_framework,
                       processes,
                       status,
                       requested_at,
                       examination_results,
                       comment
                 FROM identity_verification_applications
          """;
}
