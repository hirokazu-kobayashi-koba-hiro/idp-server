package org.idp.server.core.adapters.datasource.federation.config.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.oidc.federation.FederationConfigurationIdentifier;
import org.idp.server.core.oidc.federation.FederationType;
import org.idp.server.core.oidc.federation.sso.SsoProvider;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class MysqlExecutor implements FederationConfigurationSqlExecutor {

  String selectSql =
      """
            SELECT id, type, sso_provider, payload
             FROM federation_configurations \n
          """;

  @Override
  public Map<String, String> selectOne(
      Tenant tenant, FederationType federationType, SsoProvider ssoProvider) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        selectSql
            + """
                    WHERE tenant_id = ?
                    AND type = ?
                    AND sso_provider= ?
                    """;
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());
    params.add(federationType.name());
    params.add(ssoProvider.name());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectOne(
      Tenant tenant, FederationConfigurationIdentifier identifier) {
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
