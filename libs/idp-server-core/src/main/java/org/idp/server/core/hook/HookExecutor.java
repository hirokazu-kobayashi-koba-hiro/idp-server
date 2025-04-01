package org.idp.server.core.hook;

import org.idp.server.core.tenant.Tenant;

public interface HookExecutor {

  HookType type();

  HookResult execute(
      Tenant tenant, HookTriggerType type, HookRequest request, HookConfiguration configuration);
}
