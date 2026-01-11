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

/**
 * Repository for system-wide configuration.
 *
 * <p>This repository manages configuration that applies across all tenants, stored in a dedicated
 * system_config table.
 *
 * <p>Unlike tenant-specific repositories, this does not require a Tenant parameter as the
 * configuration is global.
 */
public interface SystemConfigurationRepository {

  /**
   * Retrieves the system configuration.
   *
   * <p>If no configuration exists in the database, returns a default configuration.
   *
   * @return the system configuration
   */
  SystemConfiguration find();

  /**
   * Registers or updates the system configuration.
   *
   * @param configuration the configuration to register
   */
  void register(SystemConfiguration configuration);
}
