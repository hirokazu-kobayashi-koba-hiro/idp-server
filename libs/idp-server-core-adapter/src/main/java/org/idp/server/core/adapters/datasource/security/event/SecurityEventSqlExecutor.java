package org.idp.server.core.adapters.datasource.security.event;

import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.SecurityEvents;
import org.idp.server.platform.security.event.SecurityEventSearchCriteria;

public interface SecurityEventSqlExecutor {
  void insert(SecurityEvent securityEvent);

  SecurityEvents selectListByUser(String eventServerId, String userId);

  SecurityEvents selectList(String eventServerId, SecurityEventSearchCriteria criteria);
}
