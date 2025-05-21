package org.idp.server.core.adapters.datasource.authentication.config.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.oidc.authentication.AuthenticationConfigurationIdentifier;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class MysqlExecutor implements AuthenticationConfigSqlExecutor {

  String selectSql =
      """
           SELECT id, type, payload
            FROM authentication_configuration \n
          """;

  @Override
  public Map<String, String> selectOne(Tenant tenant, String type) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        selectSql
            + """
                WHERE tenant_id = ?
                AND type = ?
                AND enabled = true
                """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());
    params.add(type);

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectOne(
      Tenant tenant, AuthenticationConfigurationIdentifier identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        selectSql
            + """
                WHERE tenant_id = ?
                AND id = ?
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
                WHERE tenant_id = ?
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
