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

package org.idp.server.core.extension.identity.verification.configuration.process;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.json.JsonReadable;

/**
 * Process dependencies configuration for identity verification.
 *
 * <p>Defines execution order constraints, retry policy, and status-based execution control.
 *
 * <p><b>Configuration Example:</b>
 *
 * <pre>{@code
 * {
 *   "dependencies": {
 *     "required_processes": ["apply", "crm-registration"],
 *     "allow_retry": false,
 *     "allow_during_statuses": ["REQUESTED", "APPLYING"]
 *   }
 * }
 * }</pre>
 *
 * <p><b>Usage Pattern:</b>
 *
 * <ul>
 *   <li><b>Initial Process</b>: Empty required_processes, allow_retry=false
 *   <li><b>Sequential Process</b>: Previous process in required_processes
 *   <li><b>Retry-Safe Process</b>: allow_retry=true (e.g., external API calls)
 *   <li><b>Status-Restricted</b>: allow_during_statuses for cancellation
 * </ul>
 *
 * @see IdentityVerificationProcessConfiguration
 */
public class ProcessDependencies implements JsonReadable {

  List<String> requiredProcesses = new ArrayList<>();
  boolean allowRetry = false;
  List<String> allowDuringStatuses = new ArrayList<>();

  public ProcessDependencies() {}

  public ProcessDependencies(
      List<String> requiredProcesses, boolean allowRetry, List<String> allowDuringStatuses) {
    this.requiredProcesses = requiredProcesses != null ? requiredProcesses : new ArrayList<>();
    this.allowRetry = allowRetry;
    this.allowDuringStatuses =
        allowDuringStatuses != null ? allowDuringStatuses : new ArrayList<>();
  }

  /**
   * Get list of processes that must be completed before executing current process.
   *
   * @return List of required process names (empty list means no dependencies)
   */
  public List<String> requiredProcesses() {
    return requiredProcesses;
  }

  /**
   * Check if retry is allowed for this process.
   *
   * @return true if the process can be executed multiple times
   */
  public boolean allowRetry() {
    return allowRetry;
  }

  /**
   * Get list of application statuses during which this process can be executed.
   *
   * @return List of allowed status names (empty list means no status restrictions)
   */
  public List<String> allowDuringStatuses() {
    return allowDuringStatuses;
  }

  /**
   * Check if this process has any required dependencies.
   *
   * @return true if there are required processes
   */
  public boolean hasRequiredProcesses() {
    return requiredProcesses != null && !requiredProcesses.isEmpty();
  }

  /**
   * Check if this process has status restrictions.
   *
   * @return true if there are status restrictions
   */
  public boolean hasStatusRestrictions() {
    return allowDuringStatuses != null && !allowDuringStatuses.isEmpty();
  }

  /**
   * Check if execution is allowed during the given status.
   *
   * @param currentStatus Current application status
   * @return true if execution is allowed (no restrictions or status is in allowed list)
   */
  public boolean isAllowedDuringStatus(String currentStatus) {
    if (!hasStatusRestrictions()) {
      return true;
    }
    return allowDuringStatuses.contains(currentStatus);
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    if (requiredProcesses != null && !requiredProcesses.isEmpty()) {
      map.put("required_processes", requiredProcesses);
    }
    map.put("allow_retry", allowRetry);
    if (allowDuringStatuses != null && !allowDuringStatuses.isEmpty()) {
      map.put("allow_during_statuses", allowDuringStatuses);
    }
    return map;
  }
}
