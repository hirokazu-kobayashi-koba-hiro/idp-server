package org.idp.server.core.identity.event;

import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public interface UserLifecycleEventApi {

  void handle(TenantIdentifier tenantIdentifier, UserLifecycleEvent userLifecycleEvent);
}
