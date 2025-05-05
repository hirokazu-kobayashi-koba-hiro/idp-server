package org.idp.server.core.identity;

import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;

public class UserLifecycleEvent {
  Tenant tenant;
  User user;
  UserLifecycleOperation lifecycleOperation;

  public UserLifecycleEvent() {}

  public UserLifecycleEvent(Tenant tenant, User user, UserLifecycleOperation lifecycleOperation) {
    this.tenant = tenant;
    this.user = user;
    this.lifecycleOperation = lifecycleOperation;
  }

  public Tenant tenant() {
    return tenant;
  }

  public User user() {
    return user;
  }

  public UserLifecycleOperation lifecycleOperation() {
    return lifecycleOperation;
  }

  public TenantIdentifier tenantIdentifier() {
    return tenant.identifier();
  }
}
