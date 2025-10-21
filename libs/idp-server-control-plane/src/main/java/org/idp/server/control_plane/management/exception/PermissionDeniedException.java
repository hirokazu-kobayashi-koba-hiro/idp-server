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

package org.idp.server.control_plane.management.exception;

import java.util.Map;
import java.util.Set;
import org.idp.server.control_plane.base.definition.AdminPermissions;

/**
 * Exception thrown when an operator lacks required permissions.
 *
 * <p>Corresponds to HTTP 403 Forbidden status.
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
public class PermissionDeniedException extends ManagementApiException {

  private final AdminPermissions required;
  private final Set<String> actual;

  public PermissionDeniedException(AdminPermissions required, Set<String> actual) {
    super(
        String.format(
            "permission denied required permission %s, but %s",
            required.valuesAsString(), String.join(",", actual)));
    this.required = required;
    this.actual = actual;
  }

  @Override
  public String errorCode() {
    return "access_denied";
  }

  @Override
  public Map<String, Object> errorDetails() {
    return Map.of();
  }

  public AdminPermissions requiredPermissions() {
    return required;
  }

  public Set<String> actualPermissions() {
    return actual;
  }
}
