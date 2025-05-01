package org.idp.server.core.adapters.datasource.identity.verification.application.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.basic.datasource.SqlExecutor;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.verification.application.IdentityVerificationApplicationIdentifier;
import org.idp.server.core.identity.verification.application.IdentityVerificationApplicationQueries;
import org.idp.server.core.identity.verification.delegation.ExternalWorkflowApplicationIdentifier;
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
  public Map<String, String> selectOne(
      Tenant tenant, ExternalWorkflowApplicationIdentifier identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        selectSql
            + " "
            + """
                 WHERE external_application_id = ?
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
                     AND tenant_id = ?
                     ORDER BY requested_at DESC;
                    """;

    List<Object> params = new ArrayList<>();
    params.add(user.sub());
    params.add(tenant.identifierValue());

    return sqlExecutor.selectList(sqlTemplate, params);
  }

  @Override
  public List<Map<String, String>> selectList(
      Tenant tenant, User user, IdentityVerificationApplicationQueries queries) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    StringBuilder sqlBuilder = new StringBuilder();
    sqlBuilder.append(selectSql);
    sqlBuilder.append(" WHERE user_id = ? AND tenant_id = ?");

    List<Object> params = new ArrayList<>();
    params.add(user.sub());
    params.add(tenant.identifierValue());

    if (queries.hasId()) {
      sqlBuilder.append(" AND id = ?");
      params.add(queries.id());
    }
    if (queries.hasType()) {
      sqlBuilder.append(" AND verification_type = ?");
      params.add(queries.type());
    }
    if (queries.hasClientId()) {
      sqlBuilder.append(" AND client_id = ?");
      params.add(queries.clientId());
    }
    if (queries.hasExternalWorkflowApplicationId()) {
      sqlBuilder.append(" AND external_application_id = ?");
      params.add(queries.externalWorkflowApplicationId());
    }
    if (queries.hasExternalWorkflowDelegation()) {
      sqlBuilder.append(" AND external_workflow_delegation = ?");
      params.add(queries.externalWorkflowDelegation());
    }
    if (queries.hasTrustFramework()) {
      sqlBuilder.append(" AND trust_framework = ?");
      params.add(queries.trustFramework());
    }
    if (queries.hasStatus()) {
      sqlBuilder.append(" AND status = ?");
      params.add(queries.status());
    }

    sqlBuilder.append(" ORDER BY requested_at DESC");

    String sql = sqlBuilder.toString();
    return sqlExecutor.selectList(sql, params);
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
                       examination_results,
                       processes,
                       status,
                       requested_at,
                       examination_results,
                       comment
                 FROM identity_verification_applications
          """;
}
