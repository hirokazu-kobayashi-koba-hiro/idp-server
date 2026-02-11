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

package org.idp.server.core.adapters.datasource.security.event.command;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.security.SecurityEvent;

public class PostgresqlExecutor implements SecurityEventSqlExecutor {

  JsonConverter converter = JsonConverter.snakeCaseInstance();

  @Override
  public void insert(SecurityEvent securityEvent) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
                INSERT INTO security_event (
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
                )
                VALUES (
                ?::uuid,
                ?,
                ?,
                ?::uuid,
                ?,
                ?,
                ?,
                ?::uuid,
                ?,
                ?,
                ?::INET,
                ?,
                ?::jsonb,
                ?
                ) ON CONFLICT DO NOTHING;
                """;
    List<Object> params = new ArrayList<>();
    params.add(securityEvent.identifier().valueAsUuid());
    params.add(securityEvent.type().value());
    params.add(securityEvent.description().value());
    params.add(securityEvent.tenant().idAsUuid());
    params.add(securityEvent.tenant().name());
    params.add(securityEvent.client().id());
    params.add(securityEvent.client().name());

    if (securityEvent.hasUser()) {
      params.add(securityEvent.user().subAsUuid());
      params.add(securityEvent.user().name());
      params.add(securityEvent.user().exSub());
    } else {
      params.add(null);
      params.add(null);
      params.add(null);
    }

    params.add(securityEvent.ipAddressValue());
    params.add(securityEvent.userAgentValue());

    params.add(converter.write(securityEvent.detail().toMap()));
    params.add(securityEvent.createdAt().value());

    sqlExecutor.execute(sqlTemplate, params);
  }
}
