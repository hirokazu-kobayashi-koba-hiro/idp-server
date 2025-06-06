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

package org.idp.server.core.adapters.datasource.multi_tenancy.tenant.invitation.command;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.control_plane.management.tenant.invitation.operation.TenantInvitation;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class PostgresqlExecutor implements TenantInvitationSqlExecutor {

  @Override
  public void insert(Tenant tenant, TenantInvitation invitation) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlOrganizationTemplate =
        """
                    INSERT INTO tenant_invitation (
                    id,
                    tenant_id,
                    tenant_name,
                    email,
                    role_id,
                    role_name,
                    url,
                    status,
                    expires_in,
                    created_at,
                    expires_at,
                    updated_at
                    )
                    VALUES (
                    ?::uuid,
                    ?::uuid,
                    ?,
                    ?,
                    ?::uuid,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?
                    );
                    """;
    List<Object> params = new ArrayList<>();
    params.add(invitation.idAsUuid());
    params.add(invitation.tenantIdAsUuid());
    params.add(invitation.tenantName());
    params.add(invitation.email());
    params.add(invitation.roleIdAsUuid());
    params.add(invitation.roleName());
    params.add(invitation.url());
    params.add(invitation.status());
    params.add(invitation.expiresIn());
    params.add(invitation.createdAt().toString());
    params.add(invitation.expiresAt().toString());
    params.add(invitation.updatedAt().toString());

    sqlExecutor.execute(sqlOrganizationTemplate, params);
  }

  @Override
  public void update(Tenant tenant, TenantInvitation invitation) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
                        UPDATE tenant_invitation
                        SET status = ?,
                        updated_at = ?
                        WHERE
                        id = ?::uuid
                        AND tenant_id = ?::uuid
                        """;

    List<Object> params = new ArrayList<>();
    params.add(invitation.status());
    params.add(invitation.updatedAt().toString());
    params.add(invitation.idAsUuid());
    params.add(invitation.tenantIdAsUuid());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void delete(Tenant tenant, TenantInvitation invitation) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
                    DELETE FROM tenant_invitation
                    WHERE
                    id = ?::uuid
                    AND tenant_id = ?::uuid
                    """;

    List<Object> params = new ArrayList<>();
    params.add(invitation.idAsUuid());
    params.add(invitation.tenantIdAsUuid());

    sqlExecutor.execute(sqlTemplate, params);
  }
}
