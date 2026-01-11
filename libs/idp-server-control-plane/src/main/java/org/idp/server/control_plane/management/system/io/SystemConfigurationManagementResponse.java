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

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.system.SystemConfiguration;

public class SystemConfigurationManagementResponse {

  private SystemConfigurationManagementStatus status;
  private Map<String, Object> contents;

  public SystemConfigurationManagementResponse() {}

  public SystemConfigurationManagementResponse(
      SystemConfigurationManagementStatus status, Map<String, Object> contents) {
    this.status = status;
    this.contents = contents;
  }

  public static SystemConfigurationManagementResponse success(SystemConfiguration configuration) {
    return new SystemConfigurationManagementResponse(
        SystemConfigurationManagementStatus.OK, configuration.toMap());
  }

  public static SystemConfigurationManagementResponse updated(SystemConfiguration configuration) {
    Map<String, Object> contents = new HashMap<>(configuration.toMap());
    contents.put("message", "Configuration updated");
    return new SystemConfigurationManagementResponse(
        SystemConfigurationManagementStatus.UPDATED, contents);
  }

  public static SystemConfigurationManagementResponse validationPassed(
      SystemConfiguration configuration) {
    Map<String, Object> contents = new HashMap<>(configuration.toMap());
    contents.put("message", "Validation passed (dry-run)");
    return new SystemConfigurationManagementResponse(
        SystemConfigurationManagementStatus.VALIDATION_PASSED, contents);
  }

  public static SystemConfigurationManagementResponse error(String message) {
    Map<String, Object> contents = new HashMap<>();
    contents.put("error", message);
    return new SystemConfigurationManagementResponse(
        SystemConfigurationManagementStatus.ERROR, contents);
  }

  public SystemConfigurationManagementStatus status() {
    return status;
  }

  public Map<String, Object> contents() {
    return contents;
  }

  public int statusCode() {
    return status.statusCode();
  }

  public boolean isOk() {
    return this.status.isOk();
  }
}
