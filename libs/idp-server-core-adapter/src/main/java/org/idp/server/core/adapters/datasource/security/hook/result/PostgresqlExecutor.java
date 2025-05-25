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

package org.idp.server.core.adapters.datasource.security.hook.result;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.hook.SecurityEventHookResult;

public class PostgresqlExecutor implements SecurityEventHoolResultSqlExecutor {

  JsonConverter converter = JsonConverter.snakeCaseInstance();

  @Override
  public void insert(
      Tenant tenant, SecurityEvent securityEvent, List<SecurityEventHookResult> results) {
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
       status
      )
      VALUES
  """);

    String eventPayload = converter.write(securityEvent.detail().toMap());

    List<Object> params = new ArrayList<>();
    for (int i = 0; i < results.size(); i++) {
      SecurityEventHookResult result = results.get(i);
      if (i > 0) {
        sql.append(", ");
      }
      sql.append("(?::uuid, ?::uuid, ?::uuid, ?, ?, ?::jsonb, ?)");
      params.add(result.identifier().value());
      params.add(tenant.identifierValue());
      params.add(securityEvent.identifier().value());
      params.add(securityEvent.type().value());
      params.add(result.type().name());
      params.add(eventPayload);
      params.add(result.status().name());
    }
    sql.append(";");

    sqlExecutor.execute(sql.toString(), params);
  }
}
