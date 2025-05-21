package org.idp.server.core.adapters.datasource.identity.command;

import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.identity.UserIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface UserCommandSqlExecutor {
  void insert(Tenant tenant, User user);

  void update(Tenant tenant, User user);

  void delete(Tenant tenant, UserIdentifier userIdentifier);

  void upsertRoles(Tenant tenant, User user);

  void upsertAssignedTenants(Tenant tenant, User user);

  void upsertCurrentTenant(Tenant tenant, User user);

  void upsertAssignedOrganizations(Tenant tenant, User user);

  void upsertCurrentOrganization(Tenant tenant, User user);
}
