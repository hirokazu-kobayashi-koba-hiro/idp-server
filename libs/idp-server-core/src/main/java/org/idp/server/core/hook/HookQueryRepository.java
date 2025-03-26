package org.idp.server.core.hook;

import org.idp.server.core.tenant.Tenant;

public interface HookQueryRepository {

  HookConfigurations find(Tenant tenant, HookTriggerType triggerType);
}
