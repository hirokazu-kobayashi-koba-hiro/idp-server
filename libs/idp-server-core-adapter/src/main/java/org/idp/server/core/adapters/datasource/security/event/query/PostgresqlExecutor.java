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

package org.idp.server.core.adapters.datasource.security.event.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.SecurityEventQueries;
import org.idp.server.platform.security.event.SecurityEventIdentifier;

public class PostgresqlExecutor implements SecurityEventSqlExecutor {

  @Override
  public Map<String, String> selectCount(Tenant tenant, SecurityEventQueries queries) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String selectSql = """
            SELECT COUNT(*) FROM security_event
            """;
    StringBuilder sql = new StringBuilder(selectSql).append(" WHERE tenant_id = ?::uuid");
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());

    if (queries.hasFrom() && queries.hasTo()) {
      sql.append(" AND created_at BETWEEN ? AND ?");
      params.add(queries.from());
      params.add(queries.to());
    } else if (queries.hasFrom()) {
      sql.append(" AND created_at >= ?");
      params.add(queries.from());
    } else if (queries.hasTo()) {
      sql.append(" AND created_at <= ?");
      params.add(queries.to());
    }

    if (queries.hasId()) {
      sql.append(" AND id = ?::uuid");
      params.add(queries.idAsUuid());
    }

    if (queries.hasClientId()) {
      sql.append(" AND client_id = ?");
      params.add(queries.clientId());
    }

    if (queries.hasUserId()) {
      sql.append(" AND user_id = ?::uuid");
      params.add(queries.userIdAsUuid());
    }

    if (queries.hasExternalUserId()) {
      sql.append(" AND external_user_id = ?");
      params.add(queries.externalUserId());
    }

    if (queries.hasEventType()) {
      sql.append(" AND type = ?");
      params.add(queries.eventType());
    }

    if (queries.hasDetails()) {
      for (Map.Entry<String, String> entry : queries.details().entrySet()) {
        String key = entry.getKey();
        String value = entry.getValue();
        sql.append(" AND detail ->> ? = ?");
        params.add(key);
        params.add(value);
      }
    }

    return sqlExecutor.selectOne(sql.toString(), params);
  }

  @Override
  public List<Map<String, String>> selectList(Tenant tenant, SecurityEventQueries queries) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    StringBuilder sql = new StringBuilder(selectSql).append(" WHERE tenant_id = ?::uuid");
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());

    if (queries.hasFrom() && queries.hasTo()) {
      sql.append(" AND created_at BETWEEN ? AND ?");
      params.add(queries.from());
      params.add(queries.to());
    } else if (queries.hasFrom()) {
      sql.append(" AND created_at >= ?");
      params.add(queries.from());
    } else if (queries.hasTo()) {
      sql.append(" AND created_at <= ?");
      params.add(queries.to());
    }

    if (queries.hasId()) {
      sql.append(" AND id = ?::uuid");
      params.add(queries.idAsUuid());
    }

    if (queries.hasClientId()) {
      sql.append(" AND client_id = ?");
      params.add(queries.clientId());
    }

    if (queries.hasUserId()) {
      sql.append(" AND user_id = ?::uuid");
      params.add(queries.userIdAsUuid());
    }

    if (queries.hasExternalUserId()) {
      sql.append(" AND external_user_id = ?");
      params.add(queries.externalUserId());
    }

    if (queries.hasEventType()) {
      sql.append(" AND type = ?");
      params.add(queries.eventType());
    }

    if (queries.hasDetails()) {
      for (Map.Entry<String, String> entry : queries.details().entrySet()) {
        String key = entry.getKey();
        String value = entry.getValue();
        sql.append(" AND detail ->> ? = ?");
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
  public Map<String, String> selectOne(Tenant tenant, SecurityEventIdentifier identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate = selectSql + " WHERE tenant_id = ?::uuid AND id = ?::uuid";
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierUUID());
    params.add(identifier.valueAsUuid());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  String selectSql =
      """
            SELECT
            id,
            type,
            description,
            tenant_id,
            tenant_name,
            client_id,
            client_name,
            user_id,
            user_name,
            external_user_id,
            ip_address,
            user_agent,
            detail,
            created_at
            FROM
            security_event
            """;
}
