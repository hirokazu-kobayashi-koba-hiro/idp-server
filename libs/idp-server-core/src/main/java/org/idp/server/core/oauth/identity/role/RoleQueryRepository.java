package org.idp.server.core.oauth.identity.role;

import org.idp.server.core.tenant.Tenant;

public interface RoleQueryRepository {

  Roles findAll(Tenant tenant);
}
