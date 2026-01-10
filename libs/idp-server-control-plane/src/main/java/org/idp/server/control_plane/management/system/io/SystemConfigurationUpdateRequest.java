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

package org.idp.server.control_plane.management.system.io;

import java.util.Map;
import org.idp.server.platform.system.SystemConfiguration;

public class SystemConfigurationUpdateRequest {

  private Map<String, Object> configuration;

  public SystemConfigurationUpdateRequest() {}

  public SystemConfigurationUpdateRequest(Map<String, Object> configuration) {
    this.configuration = configuration;
  }

  public Map<String, Object> configuration() {
    return configuration;
  }

  public SystemConfiguration toSystemConfiguration() {
    return SystemConfiguration.fromMap(configuration);
  }

  public boolean hasConfiguration() {
    return configuration != null && !configuration.isEmpty();
  }
}
