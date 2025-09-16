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

package org.idp.server.core.adapters.datasource.audit.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.audit.AuditLogIdentifier;
import org.idp.server.platform.audit.AuditLogQueries;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class PostgresqlExecutor implements AuditLogSqlExecutor {

  @Override
  public Map<String, String> selectCount(Tenant tenant, AuditLogQueries queries) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String selectSql = """
            SELECT COUNT(*) as count FROM audit_log
            """;
    StringBuilder sql = new StringBuilder(selectSql).append(" WHERE tenant_id = ?::uuid");
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());
    sql.append(" AND created_at BETWEEN ? AND ?");
    params.add(queries.from());
    params.add(queries.to());

    if (queries.hasId()) {
      sql.append(" AND id = ?");
      params.add(queries.id());
    }

    if (queries.hasType()) {
      sql.append(" AND type = ?");
      params.add(queries.type());
    }

    if (queries.hasDescription()) {
      sql.append(" AND description = ?");
      params.add(queries.description());
    }

    if (queries.hasTargetResource()) {
      sql.append(" AND target_resource = ?");
      params.add(queries.targetResource());
    }

    if (queries.hasTargetAction()) {
      sql.append(" AND target_resource_action = ?");
      params.add(queries.targetAction());
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
  public List<Map<String, String>> selectList(Tenant tenant, AuditLogQueries queries) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    StringBuilder sql = new StringBuilder(selectSql).append(" WHERE tenant_id = ?::uuid");
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());
    sql.append(" AND created_at BETWEEN ? AND ?");
    params.add(queries.from());
    params.add(queries.to());

    if (queries.hasId()) {
      sql.append(" AND id = ?");
      params.add(queries.id());
    }

    if (queries.hasType()) {
      sql.append(" AND type = ?");
      params.add(queries.type());
    }

    if (queries.hasDescription()) {
      sql.append(" AND description = ?");
      params.add(queries.description());
    }

    if (queries.hasTargetResource()) {
      sql.append(" AND target_resource = ?");
      params.add(queries.targetResource());
    }

    if (queries.hasTargetAction()) {
      sql.append(" AND target_resource_action = ?");
      params.add(queries.targetAction());
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
    sql.append(" LIMIT ? OFFSET ?");
    params.add(queries.limit());
    params.add(queries.offset());

    return sqlExecutor.selectList(sql.toString(), params);
  }

  @Override
  public Map<String, String> selectOne(Tenant tenant, AuditLogIdentifier identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sql = selectSql + " WHERE tenant_id = ?::uuid AND id = ?::uuid";
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());
    params.add(identifier.valueAsUuid());

    return sqlExecutor.selectOne(sql, params);
  }

  String selectSql =
      """
          SELECT
          id,
          type,
          description,
          tenant_id,
          client_id,
          user_id,
          external_user_id,
          user_payload,
          target_resource,
          target_resource_action,
          before_payload,
          after_payload,
          ip_address,
          user_agent,
          dry_run,
          attributes,
          created_at
          FROM audit_log
          """;
}
