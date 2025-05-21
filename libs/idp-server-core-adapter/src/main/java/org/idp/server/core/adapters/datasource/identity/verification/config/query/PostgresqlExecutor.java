package org.idp.server.core.adapters.datasource.identity.verification.config.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.identity.verification.IdentityVerificationType;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationConfigurationIdentifier;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.datasource.SqlExecutor;

public class PostgresqlExecutor implements IdentityVerificationConfigSqlExecutor {

  String selectSql =
      """
          SELECT id, payload
          FROM identity_verification_configurations \n
          """;

  @Override
  public Map<String, String> selectOne(Tenant tenant, IdentityVerificationType type) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        selectSql
            + """
            WHERE tenant_id = ?::uuid
            AND type = ?
            AND enabled = true
            """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());
    params.add(type.name());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectOne(
      Tenant tenant, IdentityVerificationConfigurationIdentifier identifier) {

    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        selectSql
            + """
                WHERE tenant_id = ?::uuid
                AND id = ?::uuid
                """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());
    params.add(identifier.value());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public List<Map<String, String>> selectList(Tenant tenant, int limit, int offset) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        selectSql
            + """
                WHERE tenant_id = ?::uuid
                AND id = ?::uuid
                limit ?
                offset ?
                """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());
    params.add(limit);
    params.add(offset);

    return sqlExecutor.selectList(sqlTemplate, params);
  }
}
