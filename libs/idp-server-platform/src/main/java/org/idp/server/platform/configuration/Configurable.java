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

/**
 * Interface for configuration objects that can be enabled or disabled. Provides a uniform way to
 * control the active state of various configurations such as authentication configurations,
 * security event hooks, clients, and tenants.
 */
public interface Configurable {

  /**
   * Checks if this configuration is currently enabled.
   *
   * @return true if the configuration is enabled and should be active, false otherwise
   */
  boolean isEnabled();

  /**
   * Checks if this configuration exists and is valid. This method should verify that the
   * configuration has required fields and is in a usable state.
   *
   * @return true if the configuration exists and is valid, false otherwise
   */
  boolean exists();

  /**
   * Checks if this configuration is both enabled and valid. This is a convenience method that
   * combines isEnabled() and exists() checks.
   *
   * @return true if the configuration is enabled and exists, false otherwise
   */
  default boolean isActive() {
    return isEnabled() && exists();
  }
}
