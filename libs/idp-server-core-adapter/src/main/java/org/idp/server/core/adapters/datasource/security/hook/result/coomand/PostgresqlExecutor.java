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

package org.idp.server.core.adapters.datasource.security.hook.result.coomand;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.hook.SecurityEventHookResult;
import org.idp.server.platform.security.hook.SecurityEventHookStatus;

public class PostgresqlExecutor implements SecurityEventHoolResultSqlExecutor {

  JsonConverter converter = JsonConverter.snakeCaseInstance();

  @Override
  public void insert(Tenant tenant, SecurityEventHookResult result) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    StringBuilder sql =
        new StringBuilder(
            """
              INSERT INTO security_event_hook_results
              (
               id,
               tenant_id,
               security_event_id,
               security_event_type,
               security_event_hook,
               security_event_payload,
               security_event_hook_execution_payload,
               status
              )
              VALUES
          """);

    List<Object> params = new ArrayList<>();
    sql.append("(?::uuid, ?::uuid, ?::uuid, ?, ?, ?::jsonb, ?::jsonb, ?)");
    params.add(result.identifier().valueAsUuid());
    params.add(tenant.identifierUUID());
    params.add(result.securityEvent().identifier().valueAsUuid());
    params.add(result.securityEvent().type().value());
    params.add(result.type().name());
    params.add(converter.write(result.securityEvent().toMap()));
    String executionPayload = converter.write(result.contents());
    params.add(executionPayload);
    params.add(result.status().name());
    sql.append(";");

    sqlExecutor.execute(sql.toString(), params);
  }

  @Override
  public void updateStatus(
      Tenant tenant, SecurityEventHookResult result, SecurityEventHookStatus status) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
            UPDATE security_event_hook_results
            SET status = ?
            WHERE id = ?::uuid
            AND tenant_id = ?::uuid;
            """;

    List<Object> params = new ArrayList<>();
    params.add(status.name());
    params.add(result.identifier().valueAsUuid());
    params.add(tenant.identifier().valueAsUuid());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void bulkInsert(Tenant tenant, List<SecurityEventHookResult> results) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    StringBuilder sql =
        new StringBuilder(
            """
      INSERT INTO security_event_hook_results
      (
       id,
       tenant_id,
       security_event_id,
       security_event_type,
       security_event_hook,
       security_event_payload,
       security_event_hook_execution_payload,
       status
      )
      VALUES
  """);

    List<Object> params = new ArrayList<>();
    for (int i = 0; i < results.size(); i++) {
      SecurityEventHookResult result = results.get(i);
      if (i > 0) {
        sql.append(", ");
      }
      sql.append("(?::uuid, ?::uuid, ?::uuid, ?, ?, ?::jsonb, ?::jsonb, ?)");
      params.add(result.identifier().valueAsUuid());
      params.add(tenant.identifierUUID());
      params.add(result.securityEvent().identifier().valueAsUuid());
      params.add(result.securityEvent().type().value());
      params.add(result.type().name());
      params.add(converter.write(result.securityEvent().toMap()));
      String executionPayload = converter.write(result.contents());
      params.add(executionPayload);
      params.add(result.status().name());
    }
    sql.append(";");

    sqlExecutor.execute(sql.toString(), params);
  }
}
