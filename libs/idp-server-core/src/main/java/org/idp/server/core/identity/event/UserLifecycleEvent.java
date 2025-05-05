package org.idp.server.core.identity.event;

import org.idp.server.core.identity.User;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;

public class UserLifecycleEvent {
  Tenant tenant;
  User user;
  UserLifecycleType lifecycleOperation;

  public UserLifecycleEvent() {}

  public UserLifecycleEvent(Tenant tenant, User user, UserLifecycleType lifecycleOperation) {
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

  public UserLifecycleType lifecycleType() {
    return lifecycleOperation;
  }

  public TenantIdentifier tenantIdentifier() {
    return tenant.identifier();
  }
}
