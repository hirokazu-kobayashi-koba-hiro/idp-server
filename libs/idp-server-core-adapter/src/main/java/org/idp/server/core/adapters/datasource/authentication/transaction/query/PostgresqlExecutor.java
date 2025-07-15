/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.core.adapters.datasource.authentication.transaction.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.oidc.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.oidc.authentication.AuthenticationTransactionQueries;
import org.idp.server.core.oidc.authentication.AuthorizationIdentifier;
import org.idp.server.core.oidc.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class PostgresqlExecutor implements AuthenticationTransactionQuerySqlExecutor {

  @Override
  public Map<String, String> selectOne(
      Tenant tenant, AuthenticationTransactionIdentifier identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        selectSql
            + " "
            + """
            WHERE id = ?::uuid
            AND tenant_id = ?::uuid
            """;
    List<Object> params = new ArrayList<>();
    params.add(identifier.valueAsUuid());
    params.add(tenant.identifierUUID());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectOne(Tenant tenant, AuthorizationIdentifier identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        selectSql
            + " "
            + """
            WHERE authorization_id = ?::uuid
            AND tenant_id = ?::uuid
            """;
    List<Object> params = new ArrayList<>();
    params.add(identifier.valueAsUuid());
    params.add(tenant.identifierUUID());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public List<Map<String, String>> selectListByDeviceId(
      Tenant tenant,
      AuthenticationDeviceIdentifier authenticationDeviceIdentifier,
      AuthenticationTransactionQueries queries) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    StringBuilder sql =
        new StringBuilder(selectSql)
            .append(" WHERE tenant_id = ?::uuid AND authentication_device_id = ?::uuid");
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());
    params.add(authenticationDeviceIdentifier.valueAsUuid());

    if (queries.hasFrom()) {
      sql.append(" AND created_at >= ?");
      params.add(queries.from());
    }

    if (queries.hasTo()) {
      sql.append(" AND created_at <= ?");
      params.add(queries.to());
    }

    if (queries.hasId()) {
      sql.append(" AND id = ?");
      params.add(queries.id());
    }

    if (queries.hasFlow()) {
      sql.append(" AND flow = ?");
      params.add(queries.flow());
    }

    if (queries.hasAuthorizationId()) {
      sql.append(" AND authorization_id = ?::uuid");
      params.add(queries.authorizationIdAsUuid());
    }

    if (queries.hasClientId()) {
      sql.append(" AND client_id = ?");
      params.add(queries.clientId());
    }

    if (queries.isExcludeExpired()) {
      sql.append(" AND expires_at > ?");
      params.add(SystemDateTime.now());
    }

    if (queries.hasAttributes()) {
      for (Map.Entry<String, String> entry : queries.attributes().entrySet()) {
        String key = entry.getKey();
        String value = entry.getValue();
        sql.append(" AND attributes ->> ? = ?");
        params.add(key);
        params.add(value);
      }
    }

    sql.append(" ORDER BY created_at DESC");
    sql.append(" LIMIT ?");
    sql.append(" OFFSET ?");
    params.add(queries.limit());
    params.add(queries.offset());

    return sqlExecutor.selectList(sql.toString(), params);
  }

  @Override
  public Map<String, String> selectCount(Tenant tenant, AuthenticationTransactionQueries queries) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM authentication_transaction");
    sql.append(" WHERE tenant_id = ?::uuid");
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierUUID());

    if (queries.hasFrom()) {
      sql.append(" AND created_at >= ?");
      params.add(queries.from());
    }

    if (queries.hasTo()) {
      sql.append(" AND created_at <= ?");
      params.add(queries.to());
    }

    if (queries.hasId()) {
      sql.append(" AND id = ?");
      params.add(queries.id());
    }

    if (queries.hasFlow()) {
      sql.append(" AND flow = ?");
      params.add(queries.flow());
    }

    if (queries.hasAuthorizationId()) {
      sql.append(" AND authorization_id = ?::uuid");
      params.add(queries.authorizationIdAsUuid());
    }

    if (queries.hasClientId()) {
      sql.append(" AND client_id = ?");
      params.add(queries.clientId());
    }

    if (queries.hasDeviceId()) {
      sql.append(" AND authentication_device_id = ?::uuid");
      params.add(queries.deviceIdAsUuid());
    }

    if (queries.isExcludeExpired()) {
      sql.append(" AND expires_at > ?");
      params.add(SystemDateTime.now());
    }

    if (queries.hasAttributes()) {
      for (Map.Entry<String, String> entry : queries.attributes().entrySet()) {
        String key = entry.getKey();
        String value = entry.getValue();
        sql.append(" AND attributes ->> ? = ?");
        params.add(key);
        params.add(value);
      }
    }

    return sqlExecutor.selectOne(sql.toString(), params);
  }

  @Override
  public List<Map<String, String>> selectList(
      Tenant tenant, AuthenticationTransactionQueries queries) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    StringBuilder sql = new StringBuilder(selectSql).append(" WHERE tenant_id = ?::uuid");
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());

    if (queries.hasFrom()) {
      sql.append(" AND created_at >= ?");
      params.add(queries.from());
    }

    if (queries.hasTo()) {
      sql.append(" AND created_at <= ?");
      params.add(queries.to());
    }

    if (queries.hasId()) {
      sql.append(" AND id = ?");
      params.add(queries.id());
    }

    if (queries.hasFlow()) {
      sql.append(" AND flow = ?");
      params.add(queries.flow());
    }

    if (queries.hasAuthorizationId()) {
      sql.append(" AND authorization_id = ?::uuid");
      params.add(queries.authorizationIdAsUuid());
    }

    if (queries.hasClientId()) {
      sql.append(" AND client_id = ?");
      params.add(queries.clientId());
    }

    if (queries.hasDeviceId()) {
      sql.append(" AND authentication_device_id = ?::uuid");
      params.add(queries.deviceIdAsUuid());
    }

    if (queries.isExcludeExpired()) {
      sql.append(" AND expires_at > ?");
      params.add(SystemDateTime.now());
    }

    if (queries.hasAttributes()) {
      for (Map.Entry<String, String> entry : queries.attributes().entrySet()) {
        String key = entry.getKey();
        String value = entry.getValue();
        sql.append(" AND attributes ->> ? = ?");
        params.add(key);
        params.add(value);
      }
    }

    sql.append(" ORDER BY created_at DESC");
    sql.append(" LIMIT ?");
    sql.append(" OFFSET ?");
    params.add(queries.limit());
    params.add(queries.offset());

    return sqlExecutor.selectList(sql.toString(), params);
  }

  String selectSql =
      """
          SELECT
          id,
          tenant_id,
          tenant_payload,
          flow,
          authorization_id,
          client_id,
          client_payload,
          user_id,
          user_payload,
          context,
          authentication_device_id,
          authentication_device_payload,
          authentication_policy,
          interactions,
          attributes,
          created_at,
          expires_at
          FROM authentication_transaction
          """;
}
