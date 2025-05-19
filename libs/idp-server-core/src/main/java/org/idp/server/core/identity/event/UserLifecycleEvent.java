package org.idp.server.core.identity.event;

import org.idp.server.core.identity.User;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;

public class UserLifecycleEvent {
  Tenant tenant;
  User user;
  UserLifecycleType lifecycleOperation;
  UserLifecycleEventPayload payload;

  public UserLifecycleEvent() {}

  public UserLifecycleEvent(Tenant tenant, User user, UserLifecycleType lifecycleOperation) {
    this(tenant, user, lifecycleOperation, new UserLifecycleEventPayload());
  }

  public UserLifecycleEvent(
      Tenant tenant,
      User user,
      UserLifecycleType lifecycleOperation,
      UserLifecycleEventPayload payload) {
    this.tenant = tenant;
    this.user = user;
    this.lifecycleOperation = lifecycleOperation;
    this.payload = payload;
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

  public UserLifecycleEventPayload payload() {
    return payload;
  }

  public TenantIdentifier tenantIdentifier() {
    return tenant.identifier();
  }
}
