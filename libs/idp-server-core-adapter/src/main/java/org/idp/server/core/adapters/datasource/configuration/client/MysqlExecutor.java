package org.idp.server.core.adapters.datasource.configuration.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.basic.sql.SqlExecutor;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.oauth.client.ClientIdentifier;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.type.oauth.RequestedClientId;

public class MysqlExecutor implements ClientConfigSqlExecutor {

  JsonConverter jsonConverter;

  public MysqlExecutor() {
    this.jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
  }

  @Override
  public void insert(Tenant tenant, ClientConfiguration clientConfiguration) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
            INSERT INTO client_configuration (id, id_alias, tenant_id, payload)
            VALUES (?, ?, ?, ?)
            """;

    String payload = jsonConverter.write(clientConfiguration);
    List<Object> params = new ArrayList<>();
    params.add(clientConfiguration.clientIdentifier().value());
    params.add(clientConfiguration.clientIdAlias());
    params.add(tenant.identifierValue());
    params.add(payload);

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectByAlias(Tenant tenant, RequestedClientId requestedClientId) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplateClientIdAlias =
        """
                        SELECT id, id_alias, tenant_id, payload
                        FROM client_configuration
                        WHERE tenant_id = ? AND id_alias = ?;
                        """;
    List<Object> paramsClientIdAlias = List.of(tenant.identifierValue(), requestedClientId.value());
    return sqlExecutor.selectOne(sqlTemplateClientIdAlias, paramsClientIdAlias);
  }

  @Override
  public Map<String, String> selectById(Tenant tenant, ClientIdentifier clientIdentifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
                        SELECT id, id_alias, tenant_id, payload
                        FROM client_configuration
                        WHERE tenant_id = ? AND id = ?;
                        """;
    List<Object> params = List.of(tenant.identifierValue(), clientIdentifier.value());
    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public List<Map<String, String>> selectList(Tenant tenant, int limit, int offset) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
                        SELECT id, id_alias, tenant_id, payload
                        FROM client_configuration
                        WHERE tenant_id = ? limit ? offset ?;
                        """;
    List<Object> params = List.of(tenant.identifierValue(), limit, offset);
    return sqlExecutor.selectList(sqlTemplate, params);
  }

  @Override
  public void update(Tenant tenant, ClientConfiguration clientConfiguration) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
                UPDATE client_configuration
                SET id_alias = ?,
                payload = ?
                WHERE tenant_id = ?
                AND id = ?
                """;

    String payload = jsonConverter.write(clientConfiguration);
    List<Object> params = new ArrayList<>();
    params.add(clientConfiguration.clientIdAlias());
    params.add(payload);
    params.add(tenant.identifierValue());
    params.add(clientConfiguration.clientIdentifier().value());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void delete(Tenant tenant, RequestedClientId requestedClientId) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
                DELETE FROM client_configuration
                WHERE tenant_id = ?
                AND id = ?
                """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());
    params.add(requestedClientId.value());

    sqlExecutor.execute(sqlTemplate, params);
  }
}
