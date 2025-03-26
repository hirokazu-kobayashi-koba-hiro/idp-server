package org.idp.server.core.hook;

import org.idp.server.core.tenant.Tenant;

public interface HookQueryRepository {

  HookConfiguration get(Tenant tenant, HookTriggerType triggerType);
}
