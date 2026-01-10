/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.platform.system;

import org.idp.server.platform.datasource.cache.CacheStore;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;
import org.idp.server.platform.log.LoggerWrapper;

/**
 * Provider for SystemConfigurationResolver.
 *
 * <p>This provider creates a cached resolver that integrates with the application's cache store. If
 * the repository is not available, it falls back to a default resolver.
 */
public class SystemConfigurationResolverProvider
    implements ApplicationComponentProvider<SystemConfigurationResolver> {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(SystemConfigurationResolverProvider.class);

  @Override
  public Class<SystemConfigurationResolver> type() {
    return SystemConfigurationResolver.class;
  }

  @Override
  public SystemConfigurationResolver provide(ApplicationComponentDependencyContainer container) {
    log.info("Creating SystemConfigurationResolver");

    // Try to resolve dependencies
    CacheStore cacheStore;
    SystemConfigurationRepository repository;

    try {
      cacheStore = container.resolve(CacheStore.class);
    } catch (Exception e) {
      log.warn("CacheStore not available, using default configuration resolver");
      return new DefaultSystemConfigurationResolver();
    }

    try {
      repository = container.resolve(SystemConfigurationRepository.class);
    } catch (Exception e) {
      log.warn("SystemConfigurationRepository not available, using default configuration resolver");
      return new DefaultSystemConfigurationResolver();
    }

    log.info("CacheStore resolved: {}", cacheStore.getClass().getSimpleName());
    log.info("SystemConfigurationRepository resolved: {}", repository.getClass().getSimpleName());

    SystemConfigurationResolver resolver =
        new CachedSystemConfigurationResolver(repository, cacheStore);

    log.info("SystemConfigurationResolver created successfully with caching enabled");
    return resolver;
  }
}
