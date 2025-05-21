package org.idp.server.core.adapters.datasource.multi_tenancy.tenant.invitation.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.invitation.TenantInvitationIdentifier;

public class MysqlExecutor implements TenantInvitationSqlExecutor {

  String selectSql =
      """
          SELECT
                id,
                tenant_id,
                tenant_name,
                email,
                role_id,
                role_name,
                url,
                status,
                expires_in,
                created_at,
                expires_at,
                updated_at
          FROM tenant_invitation \n
          """;

  @Override
  public Map<String, String> selectOne(Tenant tenant, TenantInvitationIdentifier identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        selectSql + """
            WHERE id = ?
            AND tenant_id = ?;
            """;
    List<Object> params = new ArrayList<>();
    params.add(identifier.value());
    params.add(tenant.identifierValue());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public List<Map<String, String>> selectList(Tenant tenant, int limit, int offset) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        selectSql
            + """
            WHERE tenant_id = ?
            LIMIT ?
            OFFSET ?;;
            """;
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());
    params.add(limit);
    params.add(offset);

    return sqlExecutor.selectList(sqlTemplate, params);
  }
}
