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

package org.idp.server.control_plane.management.audit;

import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.management.audit.io.AuditLogManagementRequest;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Builder for AuditLogManagementContext.
 *
 * <p>Allows incremental construction of context, supporting error scenarios where data retrieval
 * may fail.
 *
 * <h2>Usage Pattern</h2>
 *
 * <pre>{@code
 * // 1. Create builder early (before potential failures)
 * AuditLogManagementContextBuilder builder =
 *     new AuditLogManagementContextBuilder(...);
 *
 * try {
 *   // 2. Service populates builder during execution
 *   builder.withResult(interaction);
 *
 *   // 3. Build complete context on success
 *   AuditableContext context = builder.build();
 * } catch (ManagementApiException e) {
 *   // 4. Build partial context on error (for audit logging)
 *   AuditableContext errorContext = builder.buildPartial(e);
 * }
 * }</pre>
 */
public class AuditLogManagementContextBuilder {

  private final User operator;
  private final OAuthToken oAuthToken;
  private final RequestAttributes requestAttributes;
  private final AuditLogManagementRequest request;
  private final TenantIdentifier tenantIdentifier;

  private AuditLog result; // nullable: null in error scenarios

  public AuditLogManagementContextBuilder(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      RequestAttributes requestAttributes,
      AuditLogManagementRequest request) {
    this.tenantIdentifier = tenantIdentifier;
    this.operator = operator;
    this.oAuthToken = oAuthToken;
    this.requestAttributes = requestAttributes;
    this.request = request;
  }

  /**
   * Sets the result state (for get/findList operations).
   *
   * @param result retrieved audit log
   * @return this builder
   */
  public AuditLogManagementContextBuilder withResult(AuditLog result) {
    this.result = result;
    return this;
  }

  /**
   * Builds complete AuditLogManagementContext.
   *
   * @return full context with result
   */
  public AuditableContext build() {
    return new AuditLogManagementContext(
        tenantIdentifier, operator, oAuthToken, requestAttributes, result, request, null);
  }

  /**
   * Builds partial AuditLogManagementContext for error scenarios.
   *
   * <p>Enables audit logging even when operation fails early (e.g., tenant retrieval, permission
   * check).
   *
   * @param exception the exception that caused the failure
   * @return partial context with error information
   */
  public AuditableContext buildPartial(ManagementApiException exception) {
    // Use available data, even if incomplete
    return new AuditLogManagementContext(
        tenantIdentifier, operator, oAuthToken, requestAttributes, result, request, exception);
  }
}
