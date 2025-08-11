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

package org.idp.server.core.adapters.datasource.identity.event;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.event.UserLifecycleEvent;
import org.idp.server.core.openid.identity.event.UserLifecycleEventResult;
import org.idp.server.core.openid.identity.event.UserLifecycleType;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class PostgresqlExecutor implements UserLifecycleEventResultSqlExecutor {

  JsonConverter converter = JsonConverter.snakeCaseInstance();

  @Override
  public void insert(
      Tenant tenant,
      UserLifecycleEvent userLifecycleEvent,
      List<UserLifecycleEventResult> results) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    StringBuilder sql =
        new StringBuilder(
            """
                INSERT INTO idp_user_lifecycle_event_result
                (
                id,
                tenant_id,
                user_id,
                lifecycle_type,
                executor_name,
                payload,
                status
                )
                VALUES
                """);

    User user = userLifecycleEvent.user();
    UserLifecycleType userLifecycleType = userLifecycleEvent.lifecycleType();
    List<Object> params = new ArrayList<>();
    for (int i = 0; i < results.size(); i++) {
      UserLifecycleEventResult result = results.get(i);
      if (i > 0) {
        sql.append(", ");
      }
      sql.append("(?::uuid, ?::uuid, ?::uuid, ?, ?, ?::jsonb, ?)");
      params.add(result.identifier().valueAsUuid());
      params.add(tenant.identifierUUID());
      params.add(user.subAsUuid());
      params.add(userLifecycleType.name());
      params.add(result.executorName());
      params.add(converter.write(result.data()));
      params.add(result.status().name());
    }
    sql.append(";");

    sqlExecutor.execute(sql.toString(), params);
  }
}
