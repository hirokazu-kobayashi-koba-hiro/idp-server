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

package org.idp.server.control_plane.management.authentication.interaction;

import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.management.authentication.interaction.io.AuthenticationInteractionManagementRequest;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.core.openid.authentication.interaction.AuthenticationInteraction;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Builder for AuthenticationInteractionManagementContext.
 *
 * <p>Allows incremental construction of context, supporting error scenarios where data retrieval
 * may fail.
 *
 * <h2>Usage Pattern</h2>
 *
 * <pre>{@code
 * // 1. Create builder early (before potential failures)
 * AuthenticationInteractionManagementContextBuilder builder =
 *     new AuthenticationInteractionManagementContextBuilder(...);
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
public class AuthenticationInteractionManagementContextBuilder {

  private final User operator;
  private final OAuthToken oAuthToken;
  private final RequestAttributes requestAttributes;
  private final AuthenticationInteractionManagementRequest request;
  private final TenantIdentifier tenantIdentifier;

  private AuthenticationInteraction result; // nullable: null in error scenarios

  public AuthenticationInteractionManagementContextBuilder(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      RequestAttributes requestAttributes,
      AuthenticationInteractionManagementRequest request) {
    this.tenantIdentifier = tenantIdentifier;
    this.operator = operator;
    this.oAuthToken = oAuthToken;
    this.requestAttributes = requestAttributes;
    this.request = request;
  }

  /**
   * Sets the result state (for get/findList operations).
   *
   * @param result retrieved authentication interaction
   * @return this builder
   */
  public AuthenticationInteractionManagementContextBuilder withResult(
      AuthenticationInteraction result) {
    this.result = result;
    return this;
  }

  /**
   * Builds complete AuthenticationInteractionManagementContext.
   *
   * @return full context with result
   */
  public AuditableContext build() {
    return new AuthenticationInteractionManagementContext(
        tenantIdentifier, operator, oAuthToken, requestAttributes, result, request, null);
  }

  /**
   * Builds partial AuthenticationInteractionManagementContext for error scenarios.
   *
   * <p>Enables audit logging even when operation fails early (e.g., tenant retrieval, permission
   * check).
   *
   * @param exception the exception that caused the failure
   * @return partial context with error information
   */
  public AuditableContext buildPartial(ManagementApiException exception) {
    // Use available data, even if incomplete
    return new AuthenticationInteractionManagementContext(
        tenantIdentifier, operator, oAuthToken, requestAttributes, result, request, exception);
  }
}
