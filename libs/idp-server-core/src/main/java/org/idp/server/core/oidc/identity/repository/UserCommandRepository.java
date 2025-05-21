package org.idp.server.core.oidc.identity.repository;

import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.identity.UserIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface UserCommandRepository {
  void register(Tenant tenant, User user);

  void update(Tenant tenant, User user);

  void delete(Tenant tenant, UserIdentifier userIdentifier);
}
