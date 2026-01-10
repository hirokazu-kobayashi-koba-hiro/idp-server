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
 * Default implementation of SystemConfigurationResolver that returns default configuration.
 *
 * <p>Use this resolver when:
 *
 * <ul>
 *   <li>No database-backed configuration is available
 *   <li>During testing
 *   <li>As a fallback when the repository is not initialized
 * </ul>
 */
public class DefaultSystemConfigurationResolver implements SystemConfigurationResolver {

  private final SystemConfiguration configuration;

  public DefaultSystemConfigurationResolver() {
    this.configuration = SystemConfiguration.defaultConfiguration();
  }

  public DefaultSystemConfigurationResolver(SystemConfiguration configuration) {
    this.configuration = configuration;
  }

  @Override
  public SystemConfiguration resolve() {
    return configuration;
  }

  @Override
  public void invalidateCache() {
    // No-op for default resolver
  }
}
