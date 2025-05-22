/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.identity.event;

import org.idp.server.core.oidc.identity.User;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

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
