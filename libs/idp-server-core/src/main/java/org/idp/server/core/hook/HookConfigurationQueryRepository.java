package org.idp.server.core.hook;

import org.idp.server.core.tenant.Tenant;

public interface HookConfigurationQueryRepository {

  HookConfigurations find(Tenant tenant, HookTriggerType triggerType);
}
