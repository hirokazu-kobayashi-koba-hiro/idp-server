package org.idp.server.core.adapters.datasource.security;

import org.idp.server.core.security.SecurityEvent;
import org.idp.server.core.security.SecurityEvents;
import org.idp.server.core.security.event.SecurityEventSearchCriteria;

public interface SecurityEventSqlExecutor {
  void insert(SecurityEvent securityEvent);

  SecurityEvents selectListByUser(String eventServerId, String userId);

  SecurityEvents selectList(String eventServerId, SecurityEventSearchCriteria criteria);
}
