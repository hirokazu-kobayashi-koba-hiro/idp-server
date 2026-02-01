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

package org.idp.server.core.adapters.datasource.security.hook.result.query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.hook.SecurityEventHookResultIdentifier;
import org.idp.server.platform.security.hook.SecurityEventHookResultQueries;

public class MysqlExecutor implements SecurityEventHookResultSqlExecutor {

  @Override
  public Map<String, String> selectCount(Tenant tenant, SecurityEventHookResultQueries queries) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String selectSql =
        """
            SELECT COUNT(*) as count FROM security_event_hook_results
            """;
    StringBuilder sql = new StringBuilder(selectSql).append(" WHERE tenant_id = ?");
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
      sql.append(" AND id = ?");
      params.add(queries.id());
    }

    if (queries.hasSecurityEventId()) {
      sql.append(" AND security_event_id = ?");
      params.add(queries.securityEventId());
    }

    if (queries.hasEventType()) {
      List<String> eventTypes = queries.eventTypes();
      if (eventTypes.size() == 1) {
        sql.append(" AND security_event_type = ?");
        params.add(eventTypes.get(0));
      } else {
        sql.append(" AND security_event_type IN (");
        sql.append(String.join(",", Collections.nCopies(eventTypes.size(), "?")));
        sql.append(")");
        params.addAll(eventTypes);
      }
    }

    if (queries.hasHookType()) {
      sql.append(" AND security_event_hook = ?");
      params.add(queries.hookType());
    }

    if (queries.hasStatus()) {
      sql.append(" AND status = ?");
      params.add(queries.status());
    }

    if (queries.hasUserId()) {
      sql.append(" AND JSON_UNQUOTE(JSON_EXTRACT(security_event_payload, '$.user.sub')) = ?");
      params.add(queries.userId());
    }

    if (queries.hasUserName()) {
      sql.append(" AND JSON_UNQUOTE(JSON_EXTRACT(security_event_payload, '$.user.name')) LIKE ?");
      params.add("%" + queries.userName() + "%");
    }

    if (queries.hasExternalUserId()) {
      sql.append(" AND JSON_UNQUOTE(JSON_EXTRACT(security_event_payload, '$.user.ex_sub')) = ?");
      params.add(queries.externalUserId());
    }

    if (queries.hasSecurityEventPayload()) {
      for (Map.Entry<String, String> entry : queries.securityEventPayload().entrySet()) {
        String key = entry.getKey();
        String value = entry.getValue();
        sql.append(" AND security_event_payload ->> ? = ?");
        params.add(key);
        params.add(value);
      }
    }

    if (queries.hasExecutionPayload()) {
      for (Map.Entry<String, String> entry : queries.securityEventExecutionPayload().entrySet()) {
        String key = entry.getKey();
        String value = entry.getValue();
        sql.append(" AND security_event_hook_execution_payload ->> ? = ?");
        params.add(key);
        params.add(value);
      }
    }

    return sqlExecutor.selectOne(sql.toString(), params);
  }

  @Override
  public List<Map<String, String>> selectList(
      Tenant tenant, SecurityEventHookResultQueries queries) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    StringBuilder sql = new StringBuilder(selectSql).append(" WHERE tenant_id = ?");
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
      sql.append(" AND id = ?");
      params.add(queries.id());
    }

    if (queries.hasSecurityEventId()) {
      sql.append(" AND security_event_id = ?");
      params.add(queries.securityEventId());
    }

    if (queries.hasEventType()) {
      List<String> eventTypes = queries.eventTypes();
      if (eventTypes.size() == 1) {
        sql.append(" AND security_event_type = ?");
        params.add(eventTypes.get(0));
      } else {
        sql.append(" AND security_event_type IN (");
        sql.append(String.join(",", Collections.nCopies(eventTypes.size(), "?")));
        sql.append(")");
        params.addAll(eventTypes);
      }
    }

    if (queries.hasHookType()) {
      sql.append(" AND security_event_hook = ?");
      params.add(queries.hookType());
    }

    if (queries.hasStatus()) {
      sql.append(" AND status = ?");
      params.add(queries.status());
    }

    if (queries.hasUserId()) {
      sql.append(" AND JSON_UNQUOTE(JSON_EXTRACT(security_event_payload, '$.user.sub')) = ?");
      params.add(queries.userId());
    }

    if (queries.hasUserName()) {
      sql.append(" AND JSON_UNQUOTE(JSON_EXTRACT(security_event_payload, '$.user.name')) LIKE ?");
      params.add("%" + queries.userName() + "%");
    }

    if (queries.hasExternalUserId()) {
      sql.append(" AND JSON_UNQUOTE(JSON_EXTRACT(security_event_payload, '$.user.ex_sub')) = ?");
      params.add(queries.externalUserId());
    }

    if (queries.hasSecurityEventPayload()) {
      for (Map.Entry<String, String> entry : queries.securityEventPayload().entrySet()) {
        String key = entry.getKey();
        String value = entry.getValue();
        sql.append(" AND security_event_payload ->> ? = ?");
        params.add(key);
        params.add(value);
      }
    }

    if (queries.hasExecutionPayload()) {
      for (Map.Entry<String, String> entry : queries.securityEventExecutionPayload().entrySet()) {
        String key = entry.getKey();
        String value = entry.getValue();
        sql.append(" AND security_event_hook_execution_payload ->> ? = ?");
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
  public Map<String, String> selectOne(
      Tenant tenant, SecurityEventHookResultIdentifier identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate = selectSql + " WHERE tenant_id = ? AND id = ?";
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierUUID());
    params.add(identifier.valueAsUuid());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  String selectSql =
      """
                SELECT
                 id,
                 tenant_id,
                 security_event_id,
                 security_event_type,
                 security_event_hook,
                 security_event_payload,
                 status,
                 created_at,
                 updated_at,
                 security_event_hook_execution_payload
                FROM
                security_event_hook_results
                """;
}
