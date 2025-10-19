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

package org.idp.server.control_plane.base;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.idp.server.control_plane.base.definition.AdminPermissions;

/**
 * Result of permission verification.
 *
 * <p>Encapsulates the outcome of permission checking with standardized error response format.
 * Eliminates duplicate error response creation code across EntryService classes.
 *
 * <h2>Error Response Format</h2>
 *
 * <pre>{@code
 * {
 *   "error": "access_denied",
 *   "error_description": "permission denied required permission [user:create], but [user:read]"
 * }
 * }</pre>
 */
public class PermissionCheckResult {

  private final boolean allowed;
  private final Map<String, Object> errorResponse;

  private PermissionCheckResult(boolean allowed, Map<String, Object> errorResponse) {
    this.allowed = allowed;
    this.errorResponse = errorResponse;
  }

  /**
   * Creates an allowed result.
   *
   * @return PermissionCheckResult indicating permission granted
   */
  public static PermissionCheckResult allowed() {
    return new PermissionCheckResult(true, null);
  }

  /**
   * Creates a denied result with error details.
   *
   * @param required the permissions that were required
   * @param actual the permissions the operator actually has
   * @return PermissionCheckResult with access_denied error
   */
  public static PermissionCheckResult denied(AdminPermissions required, Set<String> actual) {
    Map<String, Object> response = new HashMap<>();
    response.put("error", "access_denied");
    response.put(
        "error_description",
        String.format(
            "permission denied required permission %s, but %s",
            required.valuesAsString(), String.join(",", actual)));
    return new PermissionCheckResult(false, response);
  }

  /**
   * Checks if permission was granted.
   *
   * @return true if allowed, false if denied
   */
  public boolean isAllowed() {
    return allowed;
  }

  /**
   * Returns the error response map for denied permissions.
   *
   * @return error response map with "error" and "error_description" keys, null if allowed
   */
  public Map<String, Object> errorResponse() {
    return errorResponse;
  }
}
