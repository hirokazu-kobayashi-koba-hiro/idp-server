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

package org.idp.server.core.adapters.datasource.audit.command;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class PostgresqlExecutor implements AuditLogSqlExecutor {

  @Override
  public void insert(Tenant tenant, AuditLog auditLog) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
                INSERT INTO audit_log (
                id,
                type,
                description,
                tenant_id,
                client_id,
                user_id,
                user_payload,
                target_resource,
                target_resource_action,
                before_payload,
                after_payload,
                ip_address,
                user_agent,
                dry_run
                ) VALUES (
                ?::uuid,
                ?,
                ?,
                ?::uuid,
                ?,
                ?::uuid,
                ?::jsonb,
                ?,
                ?,
                ?::jsonb,
                ?::jsonb,
                ?,
                ?,
                ?
                );
                """;

    List<Object> params = new ArrayList<>();
    params.add(auditLog.identifier().valueAsUuid());
    params.add(auditLog.type());
    params.add(auditLog.description());
    params.add(auditLog.tenantId());
    params.add(auditLog.clientId());
    params.add(auditLog.userId());
    params.add(auditLog.userPayload().toJson());
    params.add(auditLog.targetResource());
    params.add(auditLog.targetResourceAction());
    params.add(auditLog.before().toJson());
    params.add(auditLog.after().toJson());
    params.add(auditLog.ipAddress());
    params.add(auditLog.userAgent());
    params.add(auditLog.dryRun());

    sqlExecutor.execute(sqlTemplate, params);
  }
}
