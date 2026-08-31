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

package org.idp.server.core.openid.oauth.configuration;

import static org.junit.jupiter.api.Assertions.*;

import org.idp.server.platform.json.JsonConverter;
import org.junit.jupiter.api.Test;

/**
 * Reading the authorization server's extension configuration.
 *
 * <p>The scope to resource mapping is consulted on every authorization request, so a tenant that
 * declares none has to read as an empty map. Reading as null would fail every request for that
 * tenant rather than behave as though no resources were modelled.
 */
class AuthorizationServerExtensionConfigurationTest {

  static final JsonConverter JSON = JsonConverter.snakeCaseInstance();

  @Test
  void readsAsEmptyWhenNoMappingIsDeclared() {
    AuthorizationServerExtensionConfiguration configuration =
        JSON.read("{}", AuthorizationServerExtensionConfiguration.class);

    assertNotNull(configuration.scopeResourceMapping());
    assertTrue(configuration.scopeResourceMapping().isEmpty());
  }

  @Test
  void readsAsEmptyWhenTheMappingIsExplicitlyNull() {
    // A stored configuration can carry the key with a null value, which overrides the field
    // initializer.
    AuthorizationServerExtensionConfiguration configuration =
        JSON.read(
            "{\"scope_resource_mapping\": null}", AuthorizationServerExtensionConfiguration.class);

    assertNotNull(configuration.scopeResourceMapping());
    assertTrue(configuration.scopeResourceMapping().isEmpty());
  }
}
