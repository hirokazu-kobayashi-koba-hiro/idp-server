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

package org.idp.server.authenticators.webauthn4j.mds;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MdsConfigurationTest {

  @Test
  void defaultConstructor_shouldSetDefaultValues() {
    MdsConfiguration config = new MdsConfiguration();

    assertFalse(config.enabled());
    assertEquals(86400, config.cacheTtlSeconds());
  }

  @Test
  void constructor_withEnabled_shouldSetDefaultTtl() {
    MdsConfiguration config = new MdsConfiguration(true);

    assertTrue(config.enabled());
    assertEquals(86400, config.cacheTtlSeconds());
  }

  @Test
  void constructor_shouldSetAllValues() {
    MdsConfiguration config = new MdsConfiguration(true, 7200);

    assertTrue(config.enabled());
    assertEquals(7200, config.cacheTtlSeconds());
  }

  @Test
  void cacheTtlSeconds_shouldReturnDefault_whenZero() {
    MdsConfiguration config = new MdsConfiguration(true, 0);

    assertEquals(86400, config.cacheTtlSeconds());
  }

  @Test
  void cacheTtlSeconds_shouldReturnDefault_whenNegative() {
    MdsConfiguration config = new MdsConfiguration(true, -100);

    assertEquals(86400, config.cacheTtlSeconds());
  }

  @Test
  void cacheTtlSeconds_shouldReturnValue_whenPositive() {
    MdsConfiguration config = new MdsConfiguration(true, 1800);

    assertEquals(1800, config.cacheTtlSeconds());
  }
}
