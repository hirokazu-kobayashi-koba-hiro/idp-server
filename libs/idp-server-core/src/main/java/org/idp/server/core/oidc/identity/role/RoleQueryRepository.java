package org.idp.server.core.oidc.identity.role;

import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface RoleQueryRepository {

  Roles findAll(Tenant tenant);
}
