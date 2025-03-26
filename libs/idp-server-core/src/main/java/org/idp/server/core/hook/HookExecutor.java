package org.idp.server.core.hook;

import org.idp.server.core.tenant.Tenant;

public interface HookExecutor {

  HookResult execute(
      Tenant tenant, HookTriggerType type, HookRequest request, HookConfiguration configuration);
}
