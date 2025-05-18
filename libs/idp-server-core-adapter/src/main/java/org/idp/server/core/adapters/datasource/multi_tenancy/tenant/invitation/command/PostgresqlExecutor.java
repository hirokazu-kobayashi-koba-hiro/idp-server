package org.idp.server.core.adapters.datasource.multi_tenancy.tenant.invitation.command;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.basic.datasource.SqlExecutor;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.invitation.TenantInvitation;

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
                    role,
                    url,
                    expires_in,
                    created_at,
                    expires_at
                    )
                    VALUES (
                    ?::uuid,
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
    List<Object> organizationParams = new ArrayList<>();
    organizationParams.add(invitation.id());
    organizationParams.add(invitation.tenantId());
    organizationParams.add(invitation.tenantName());
    organizationParams.add(invitation.email());
    organizationParams.add(invitation.role());
    organizationParams.add(invitation.url());
    organizationParams.add(invitation.expiresIn());
    organizationParams.add(invitation.createdAt().toString());
    organizationParams.add(invitation.expiresAt().toString());

    sqlExecutor.execute(sqlOrganizationTemplate, organizationParams);
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

    List<Object> organizationParams = new ArrayList<>();
    organizationParams.add(invitation.id());
    organizationParams.add(invitation.tenantId());

    sqlExecutor.execute(sqlTemplate, organizationParams);
  }
}
