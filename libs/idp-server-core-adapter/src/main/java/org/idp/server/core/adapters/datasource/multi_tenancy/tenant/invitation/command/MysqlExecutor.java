package org.idp.server.core.adapters.datasource.multi_tenancy.tenant.invitation.command;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.control_plane.management.tenant.invitation.operation.TenantInvitation;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

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
                        status,
                        expires_in,
                        created_at,
                        expires_at,
                        updated_at
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
                        id = ?
                        AND tenant_id = ?
                        """;

    List<Object> params = new ArrayList<>();
    params.add(invitation.status());
    params.add(invitation.updatedAt().toString());
    params.add(invitation.id());
    params.add(invitation.tenantId());

    sqlExecutor.execute(sqlTemplate, params);
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

    List<Object> params = new ArrayList<>();
    params.add(invitation.id());
    params.add(invitation.tenantId());

    sqlExecutor.execute(sqlTemplate, params);
  }
}
