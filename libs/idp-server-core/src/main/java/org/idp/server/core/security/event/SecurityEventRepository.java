package org.idp.server.core.security.event;

import org.idp.server.core.security.SecurityEvent;
import org.idp.server.core.security.SecurityEvents;

public interface SecurityEventRepository {
  void register(SecurityEvent securityEvent);

  SecurityEvents findBy(String eventServerId, String userId);

  SecurityEvents search(String eventServerId, SecurityEventSearchCriteria criteria);
}
