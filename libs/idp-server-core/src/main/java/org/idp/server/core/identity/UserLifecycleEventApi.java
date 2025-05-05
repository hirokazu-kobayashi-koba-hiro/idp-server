package org.idp.server.core.identity;

import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;

public interface UserLifecycleEventApi {

  void handle(TenantIdentifier tenantIdentifier, UserLifecycleEvent userLifecycleEvent);
}
