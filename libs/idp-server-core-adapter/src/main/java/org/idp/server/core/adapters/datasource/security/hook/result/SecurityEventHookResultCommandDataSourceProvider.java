/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.adapters.datasource.security.hook.result;

import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;
import org.idp.server.platform.security.repository.SecurityEventHookResultCommandRepository;

public class SecurityEventHookResultCommandDataSourceProvider
    implements ApplicationComponentProvider<SecurityEventHookResultCommandRepository> {

  @Override
  public Class<SecurityEventHookResultCommandRepository> type() {
    return SecurityEventHookResultCommandRepository.class;
  }

  @Override
  public SecurityEventHookResultCommandRepository provide(
      ApplicationComponentDependencyContainer container) {
    return new SecurityEventHoolResultCommandDataSource();
  }
}
