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

import java.util.Map;

/**
 * Interface for contexts that provide complete audit log data.
 *
 * <p>This interface defines the contract for providing all audit-relevant information from domain
 * contexts. Implementation classes hold business logic state and expose audit data through these
 * methods, eliminating the need to pass multiple parameters to AuditLogCreator.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Provide all audit log fields (except id and createdAt which are generated)
 *   <li>Encapsulate audit log generation logic within domain contexts
 *   <li>Support both success and error audit logging scenarios
 * </ul>
 *
 * <h2>Design Rationale</h2>
 *
 * <p>By having the context provide all audit log data, we achieve:
 *
 * <ul>
 *   <li>Simplified AuditLogCreator (just maps context to AuditLog)
 *   <li>Type-safe audit data encapsulation
 *   <li>Single source of truth for audit information
 * </ul>
 *
 * @see AuditLogCreator
 */
public interface AuditableContext {

  // === Basic Information ===

  /**
   * Returns the API type identifier.
   *
   * <p>Example: "UserManagementApi.create", "TenantManagementApi.update"
   *
   * @return API type identifier
   */
  String type();

  /**
   * Returns the audit log description.
   *
   * <p>Typically the entity type or operation description (e.g., "user", "tenant",
   * "authentication_config").
   *
   * @return audit log description
   */
  String description();

  // === Tenant and User Information ===

  /**
   * Returns the operation source tenant ID.
   *
   * @return source tenant identifier value
   */
  String tenantId();

  /**
   * Returns the client ID that performed the operation.
   *
   * @return client identifier value
   */
  String clientId();

  /**
   * Returns the user ID (sub claim) of the operator.
   *
   * @return user identifier value
   */
  String userId();

  /**
   * Returns the external user ID of the operator.
   *
   * @return external user identifier value, or empty string if not set
   */
  String externalUserId();

  /**
   * Returns the user payload for the operator.
   *
   * @return user data as map
   */
  Map<String, Object> userPayload();

  // === Request Information ===

  /**
   * Returns the target resource path.
   *
   * <p>Example: "/v1/management/users"
   *
   * @return target resource path
   */
  String targetResource();

  /**
   * Returns the target resource action.
   *
   * <p>Example: "create", "update", "delete"
   *
   * @return target resource action
   */
  String targetResourceAction();

  /**
   * Returns the source IP address of the request.
   *
   * @return IP address
   */
  String ipAddress();

  /**
   * Returns the User-Agent header of the request.
   *
   * @return user agent string
   */
  String userAgent();

  // === Data Payloads ===

  /**
   * Returns the original request payload.
   *
   * <p>This should be the complete HTTP request body as received.
   *
   * @return request data as map
   */
  Map<String, Object> request();

  /**
   * Returns the before state payload (for update/delete operations).
   *
   * <p>For create operations, this should return an empty map.
   *
   * @return before state data as map
   */
  Map<String, Object> before();

  /**
   * Returns the after state payload (for create/update operations).
   *
   * <p>For delete operations, this should return an empty map.
   *
   * @return after state data as map
   */
  Map<String, Object> after();

  // === Execution Result ===

  /**
   * Returns the operation outcome result.
   *
   * <p>Should be "success" for successful operations, "failure" for failed operations.
   *
   * @return outcome result
   */
  String outcomeResult();

  /**
   * Returns the outcome reason (for failures).
   *
   * <p>Should be null for successful operations, error code for failures.
   *
   * @return outcome reason or null
   */
  String outcomeReason();

  // === Target Information ===

  /**
   * Returns the target tenant ID for the operation.
   *
   * <p>For same-tenant operations (user, role, client creation), this equals the operation source
   * tenant. For cross-tenant operations (tenant creation), this is the newly created tenant ID.
   *
   * @return target tenant identifier value
   */
  String targetTenantId();

  // === Meta Information ===

  /**
   * Returns additional attributes for the audit log.
   *
   * <p>Can be used for operation-specific metadata. Return empty map if no attributes.
   *
   * @return attributes as map
   */
  Map<String, Object> attributes();

  /**
   * Returns whether this is a dry-run operation.
   *
   * <p>Dry-run operations validate the request but don't persist changes.
   *
   * @return true if dry-run, false otherwise
   */
  boolean dryRun();
}
