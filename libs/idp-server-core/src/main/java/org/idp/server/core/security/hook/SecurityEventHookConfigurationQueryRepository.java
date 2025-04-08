package org.idp.server.core.security.hook;

import org.idp.server.core.security.event.SecurityEventType;
import org.idp.server.core.tenant.Tenant;

public interface SecurityEventHookConfigurationQueryRepository {

  SecurityEventHookConfigurations find(Tenant tenant, SecurityEventType triggerType);
}
