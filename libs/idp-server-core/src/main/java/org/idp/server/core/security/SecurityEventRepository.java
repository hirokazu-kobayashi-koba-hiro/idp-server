package org.idp.server.core.security;

import org.idp.server.core.security.event.SecurityEventSearchCriteria;

public interface SecurityEventRepository {
  void register(SecurityEvent securityEvent);

  SecurityEvents findBy(String eventServerId, String userId);

  SecurityEvents search(String eventServerId, SecurityEventSearchCriteria criteria);
}
