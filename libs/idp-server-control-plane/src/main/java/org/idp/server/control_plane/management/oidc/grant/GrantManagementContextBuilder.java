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

package org.idp.server.control_plane.management.oidc.grant;

import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.oidc.grant.io.GrantManagementRequest;
import org.idp.server.core.openid.grant_management.AuthorizationGranted;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Builder for GrantManagementContext.
 *
 * <p>Allows incremental construction of context, supporting error scenarios where data retrieval
 * may fail.
 *
 * <h2>Usage Pattern</h2>
 *
 * <pre>{@code
 * // 1. Create builder early (before potential failures)
 * GrantManagementContextBuilder builder =
 *     new GrantManagementContextBuilder(...);
 *
 * try {
 *   // 2. Service populates builder during execution
 *   builder.withBefore(authorizationGranted);
 *
 *   // 3. Build complete context on success
 *   AuditableContext context = builder.build();
 * } catch (ManagementApiException e) {
 *   // 4. Build partial context on error (for audit logging)
 *   AuditableContext errorContext = builder.buildPartial(e);
 * }
 * }</pre>
 */
public class GrantManagementContextBuilder {

  private final User operator;
  private final OAuthToken oAuthToken;
  private final RequestAttributes requestAttributes;
  private final GrantManagementRequest request;
  private final TenantIdentifier tenantIdentifier;
  private final boolean dryRun;

  private AuthorizationGranted before;
  private AuthorizationGranted after;

  public GrantManagementContextBuilder(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      RequestAttributes requestAttributes,
      GrantManagementRequest request,
      boolean dryRun) {
    this.tenantIdentifier = tenantIdentifier;
    this.operator = operator;
    this.oAuthToken = oAuthToken;
    this.requestAttributes = requestAttributes;
    this.request = request;
    this.dryRun = dryRun;
  }

  /**
   * Sets the before state (for get/delete operations).
   *
   * @param before existing authorization granted
   * @return this builder
   */
  public GrantManagementContextBuilder withBefore(AuthorizationGranted before) {
    this.before = before;
    return this;
  }

  /**
   * Sets the after state.
   *
   * @param after updated authorization granted
   * @return this builder
   */
  public GrantManagementContextBuilder withAfter(AuthorizationGranted after) {
    this.after = after;
    return this;
  }

  /**
   * Builds complete GrantManagementContext.
   *
   * @return full context with before/after grant
   */
  public AuditableContext build() {
    return new GrantManagementContext(
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
   * Builds partial GrantManagementContext for error scenarios.
   *
   * <p>Enables audit logging even when operation fails early (e.g., tenant retrieval, permission
   * check).
   *
   * @param exception the exception that caused the failure
   * @return partial context with error information
   */
  public AuditableContext buildPartial(ManagementApiException exception) {
    return new GrantManagementContext(
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
