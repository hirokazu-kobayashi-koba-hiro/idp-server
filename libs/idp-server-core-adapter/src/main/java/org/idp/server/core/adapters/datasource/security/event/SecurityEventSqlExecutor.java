/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.adapters.datasource.security.event;

import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.SecurityEvents;
import org.idp.server.platform.security.event.SecurityEventSearchCriteria;

public interface SecurityEventSqlExecutor {
  void insert(SecurityEvent securityEvent);

  SecurityEvents selectListByUser(String eventServerId, String userId);

  SecurityEvents selectList(String eventServerId, SecurityEventSearchCriteria criteria);
}
