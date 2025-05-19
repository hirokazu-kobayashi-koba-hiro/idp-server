package org.idp.server.core.adapters.datasource.multi_tenancy.tenant.invitation.command;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.basic.datasource.SqlExecutor;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.invitation.TenantInvitation;

public class MysqlExecutor implements TenantInvitationSqlExecutor {

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
                expires_in,
                created_at,
                expires_at
                )
                VALUES (
                ?,
                ?,
                ?,
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
    params.add(invitation.id());
    params.add(invitation.tenantId());
    params.add(invitation.tenantName());
    params.add(invitation.email());
    params.add(invitation.roleId());
    params.add(invitation.roleName());
    params.add(invitation.url());
    params.add(invitation.expiresIn());
    params.add(invitation.createdAt().toString());
    params.add(invitation.expiresAt().toString());

    sqlExecutor.execute(sqlOrganizationTemplate, params);
  }

  @Override
  public void delete(Tenant tenant, TenantInvitation invitation) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
                DELETE FROM tenant_invitation
                WHERE
                id = ?
                AND tenant_id = ?
                """;

    List<Object> organizationParams = new ArrayList<>();
    organizationParams.add(invitation.id());
    organizationParams.add(invitation.tenantId());

    sqlExecutor.execute(sqlTemplate, organizationParams);
  }
}
