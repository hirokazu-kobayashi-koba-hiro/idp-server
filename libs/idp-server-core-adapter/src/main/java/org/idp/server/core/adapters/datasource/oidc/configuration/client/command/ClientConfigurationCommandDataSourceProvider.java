/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.adapters.datasource.oidc.configuration.client.command;

import org.idp.server.core.oidc.configuration.client.ClientConfigurationCommandRepository;
import org.idp.server.platform.datasource.cache.CacheStore;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;

public class ClientConfigurationCommandDataSourceProvider
    implements ApplicationComponentProvider<ClientConfigurationCommandRepository> {

  @Override
  public Class<ClientConfigurationCommandRepository> type() {
    return ClientConfigurationCommandRepository.class;
  }

  @Override
  public ClientConfigurationCommandRepository provide(
      ApplicationComponentDependencyContainer container) {
    CacheStore cacheStore = container.resolve(CacheStore.class);
    return new ClientConfigurationCommandDataSource(cacheStore);
  }
}
