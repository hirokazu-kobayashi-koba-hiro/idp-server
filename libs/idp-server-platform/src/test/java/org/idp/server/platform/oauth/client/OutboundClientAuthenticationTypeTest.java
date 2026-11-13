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
package org.idp.server.platform.oauth.client;

import static org.junit.jupiter.api.Assertions.*;

import org.idp.server.platform.exception.InvalidConfigurationException;
import org.junit.jupiter.api.Test;

class OutboundClientAuthenticationTypeTest {

  @Test
  void readsEverySupportedType() {
    for (OutboundClientAuthenticationType type : OutboundClientAuthenticationType.values()) {
      assertEquals(
          type,
          OutboundClientAuthenticationType.of(
              type.name(), OutboundClientAuthenticationType.client_secret_post));
    }
  }

  @Test
  void takesTheDefaultWhenNoneIsNamed() {
    assertEquals(
        OutboundClientAuthenticationType.client_secret_post,
        OutboundClientAuthenticationType.of(
            null, OutboundClientAuthenticationType.client_secret_post));
    assertEquals(
        OutboundClientAuthenticationType.none,
        OutboundClientAuthenticationType.of("", OutboundClientAuthenticationType.none));
  }

  @Test
  void refusesATypeItDoesNotKnowInsteadOfFallingBack() {
    assertThrows(
        InvalidConfigurationException.class,
        () ->
            OutboundClientAuthenticationType.of(
                "private_key_jwk", OutboundClientAuthenticationType.client_secret_post));
    assertThrows(
        InvalidConfigurationException.class,
        () ->
            OutboundClientAuthenticationType.of(
                "tls_client_auth", OutboundClientAuthenticationType.client_secret_post));
  }
}
