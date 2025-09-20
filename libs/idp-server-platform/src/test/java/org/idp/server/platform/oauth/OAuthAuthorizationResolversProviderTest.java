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

package org.idp.server.platform.oauth;

import static org.junit.jupiter.api.Assertions.*;

import org.idp.server.platform.datasource.DatabaseType;
import org.idp.server.platform.datasource.DatabaseTypeConfiguration;
import org.idp.server.platform.datasource.cache.CacheStore;
import org.idp.server.platform.datasource.cache.NoOperationCacheStore;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.plugin.ApplicationComponentContainerPluginLoader;
import org.junit.jupiter.api.Test;

public class OAuthAuthorizationResolversProviderTest {

  @Test
  void testProviderTypeAndProvide() {
    // Given
    OAuthAuthorizationResolversProvider provider = new OAuthAuthorizationResolversProvider();
    ApplicationComponentDependencyContainer dependencyContainer =
        new ApplicationComponentDependencyContainer();
    dependencyContainer.register(CacheStore.class, new NoOperationCacheStore());

    // When
    Class<OAuthAuthorizationResolvers> type = provider.type();
    OAuthAuthorizationResolvers resolvers = provider.provide(dependencyContainer);

    // Then
    assertEquals(OAuthAuthorizationResolvers.class, type);
    assertNotNull(resolvers);
    assertNotNull(resolvers.get("client_credentials"));
    assertNotNull(resolvers.get("password"));
  }

  @Test
  void testServiceProviderIntegration() {
    // Given
    ApplicationComponentDependencyContainer dependencyContainer =
        new ApplicationComponentDependencyContainer();
    dependencyContainer.register(CacheStore.class, new NoOperationCacheStore());
    dependencyContainer.register(
        DatabaseTypeConfiguration.class, new DatabaseTypeConfiguration(DatabaseType.POSTGRESQL));

    // When
    var applicationComponentContainer =
        ApplicationComponentContainerPluginLoader.load(dependencyContainer);

    // Then
    OAuthAuthorizationResolvers resolvers =
        applicationComponentContainer.resolve(OAuthAuthorizationResolvers.class);
    assertNotNull(resolvers);
    assertNotNull(resolvers.get("client_credentials"));
    assertNotNull(resolvers.get("password"));
  }
}
