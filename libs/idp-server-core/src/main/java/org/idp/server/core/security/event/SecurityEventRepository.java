package org.idp.server.core.security.event;

import org.idp.server.core.security.SecurityEvent;
import org.idp.server.core.security.SecurityEvents;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public interface SecurityEventRepository {
  void register(Tenant tenant, SecurityEvent securityEvent);

  SecurityEvents findBy(Tenant tenant, String eventServerId, String userId);

  SecurityEvents search(Tenant tenant, String eventServerId, SecurityEventSearchCriteria criteria);
}
