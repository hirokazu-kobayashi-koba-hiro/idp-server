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

package org.idp.server.control_plane.management.security.hook_result;

import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.security.hook_result.io.SecurityEventHookManagementRequest;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;

public class SecurityEventHookManagementContextBuilder {

  private final User operator;
  private final OAuthToken oAuthToken;
  private final RequestAttributes requestAttributes;
  private final SecurityEventHookManagementRequest request;
  private final TenantIdentifier tenantIdentifier;

  public SecurityEventHookManagementContextBuilder(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      RequestAttributes requestAttributes,
      SecurityEventHookManagementRequest request) {
    this.tenantIdentifier = tenantIdentifier;
    this.operator = operator;
    this.oAuthToken = oAuthToken;
    this.requestAttributes = requestAttributes;
    this.request = request;
  }

  /**
   * Builds complete UserUpdateContext.
   *
   * @return full context with before/after users
   * @throws IllegalStateException if required data is missing
   */
  public AuditableContext build() {

    return new SecurityEventHookManagementContext(
        tenantIdentifier, operator, oAuthToken, requestAttributes, request, null);
  }

  /**
   * Builds partial UserUpdateContext for error scenarios.
   *
   * @param exception the exception that caused the failure
   * @return partial context with error information
   */
  public AuditableContext buildPartial(ManagementApiException exception) {
    // Use available data, even if incomplete
    return new SecurityEventHookManagementContext(
        tenantIdentifier, operator, oAuthToken, requestAttributes, request, exception);
  }
}
