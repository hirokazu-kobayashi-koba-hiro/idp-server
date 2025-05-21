package org.idp.server.core.oidc.identity.event;

import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public interface UserLifecycleEventApi {

  void handle(TenantIdentifier tenantIdentifier, UserLifecycleEvent userLifecycleEvent);
}
