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

package org.idp.server.control_plane.management.authentication.configuration;

import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.management.authentication.configuration.io.AuthenticationConfigManagementRequest;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.core.openid.authentication.config.AuthenticationConfiguration;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Builder for AuthenticationConfigManagementContext.
 *
 * <p>Allows incremental construction of context, supporting error scenarios where data retrieval
 * may fail.
 *
 * <h2>Usage Pattern</h2>
 *
 * <pre>{@code
 * // 1. Create builder early (before potential failures)
 * AuthenticationConfigManagementContextBuilder builder =
 *     new AuthenticationConfigManagementContextBuilder(...);
 *
 * try {
 *   // 2. Service populates builder during execution
 *   builder.withAfter(configuration);
 *
 *   // 3. Build complete context on success
 *   AuditableContext context = builder.build();
 * } catch (ManagementApiException e) {
 *   // 4. Build partial context on error (for audit logging)
 *   AuditableContext errorContext = builder.buildPartial(e);
 * }
 * }</pre>
 */
public class AuthenticationConfigManagementContextBuilder {

  private final User operator;
  private final OAuthToken oAuthToken;
  private final RequestAttributes requestAttributes;
  private final AuthenticationConfigManagementRequest request;
  private final TenantIdentifier tenantIdentifier;
  private final boolean dryRun;

  private AuthenticationConfiguration before; // nullable: null in error scenarios
  private AuthenticationConfiguration after; // nullable: null in error scenarios

  public AuthenticationConfigManagementContextBuilder(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      RequestAttributes requestAttributes,
      AuthenticationConfigManagementRequest request,
      boolean dryRun) {
    this.tenantIdentifier = tenantIdentifier;
    this.operator = operator;
    this.oAuthToken = oAuthToken;
    this.requestAttributes = requestAttributes;
    this.request = request;
    this.dryRun = dryRun;
  }

  /**
   * Sets the before state (for get/update/delete operations).
   *
   * @param before existing authentication policy configuration
   * @return this builder
   */
  public AuthenticationConfigManagementContextBuilder withBefore(
      AuthenticationConfiguration before) {
    this.before = before;
    return this;
  }

  /**
   * Sets the after state (for create/update operations).
   *
   * @param after new or updated authentication policy configuration
   * @return this builder
   */
  public AuthenticationConfigManagementContextBuilder withAfter(AuthenticationConfiguration after) {
    this.after = after;
    return this;
  }

  /**
   * Builds complete AuthenticationConfigManagementContext.
   *
   * @return full context with before/after configurations
   */
  public AuditableContext build() {
    return new AuthenticationConfigManagementContext(
        tenantIdentifier,
        operator,
        oAuthToken,
        requestAttributes,
        before,
        after,
        request,
        dryRun,
        null);
  }

  /**
   * Builds partial AuthenticationConfigManagementContext for error scenarios.
   *
   * <p>Enables audit logging even when operation fails early (e.g., tenant retrieval, permission
   * check).
   *
   * @param exception the exception that caused the failure
   * @return partial context with error information
   */
  public AuditableContext buildPartial(ManagementApiException exception) {
    // Use available data, even if incomplete
    return new AuthenticationConfigManagementContext(
        tenantIdentifier,
        operator,
        oAuthToken,
        requestAttributes,
        before,
        after,
        request,
        dryRun,
        exception);
  }
}
