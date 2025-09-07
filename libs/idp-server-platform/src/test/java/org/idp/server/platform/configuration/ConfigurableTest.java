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

package org.idp.server.platform.configuration;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConfigurableTest {

  @Test
  @DisplayName("isActive returns true when both enabled and exists return true")
  void testIsActiveWhenEnabledAndExists() {
    Configurable configurable = new TestConfigurable(true, true);

    assertTrue(configurable.isActive());
  }

  @Test
  @DisplayName("isActive returns false when enabled is false")
  void testIsActiveWhenNotEnabled() {
    Configurable configurable = new TestConfigurable(false, true);

    assertFalse(configurable.isActive());
  }

  @Test
  @DisplayName("isActive returns false when exists is false")
  void testIsActiveWhenNotExists() {
    Configurable configurable = new TestConfigurable(true, false);

    assertFalse(configurable.isActive());
  }

  @Test
  @DisplayName("isActive returns false when both enabled and exists are false")
  void testIsActiveWhenBothFalse() {
    Configurable configurable = new TestConfigurable(false, false);

    assertFalse(configurable.isActive());
  }

  static class TestConfigurable implements Configurable {
    private final boolean enabled;
    private final boolean exists;

    TestConfigurable(boolean enabled, boolean exists) {
      this.enabled = enabled;
      this.exists = exists;
    }

    @Override
    public boolean isEnabled() {
      return enabled;
    }

    @Override
    public boolean exists() {
      return exists;
    }
  }
}
