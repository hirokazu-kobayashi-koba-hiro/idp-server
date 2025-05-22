/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.adapters.datasource.security.event;

import org.idp.server.basic.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.repository.SecurityEventCommandRepository;

public class SecurityEventCommandDataSource implements SecurityEventCommandRepository {

  SecurityEventSqlExecutors executors;
  JsonConverter converter;

  public SecurityEventCommandDataSource() {
    this.executors = new SecurityEventSqlExecutors();
    this.converter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public void register(Tenant tenant, SecurityEvent securityEvent) {
    SecurityEventSqlExecutor executor = executors.get(tenant.databaseType());
    executor.insert(securityEvent);
  }
}
