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

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.base.OrganizationAccessVerifier;

/**
 * Exception thrown when organization-level access control verification fails.
 *
 * <p>This exception is thrown by {@link OrganizationAccessVerifier} when any of the 4-step
 * verification process fails:
 *
 * <ol>
 *   <li>Organization membership verification
 *   <li>Tenant access verification
 *   <li>Organization-tenant relationship verification
 *   <li>Required permissions verification
 * </ol>
 *
 * <p>Unlike {@link PermissionDeniedException} which indicates insufficient permissions,
 * OrganizationAccessDeniedException indicates access control failures related to organization
 * membership, tenant assignment, or organization-tenant relationships.
 *
 * <h2>Error Response Format</h2>
 *
 * <pre>{@code
 * {
 *   "error": "organization_access_denied",
 *   "error_description": "User is not member of organization: org-123. Assigned: org-456"
 * }
 * }</pre>
 *
 * @see OrganizationAccessVerifier
 * @see ManagementApiException
 */
public class OrganizationAccessDeniedException extends ManagementApiException {

  /**
   * Creates a new organization access denied exception.
   *
   * @param errorDescription the detailed error description explaining why access was denied
   */
  public OrganizationAccessDeniedException(String errorDescription) {
    super(errorDescription);
  }

  @Override
  public String errorCode() {
    return "organization_access_denied";
  }

  @Override
  public Map<String, Object> errorDetails() {
    return new HashMap<>();
  }
}
