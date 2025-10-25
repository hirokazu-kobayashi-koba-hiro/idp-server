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

package org.idp.server.control_plane.management.identity.user;

import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.identity.user.handler.UserManagementRequest;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Builder for UserUpdateContext.
 *
 * <p>Allows incremental construction of update context, supporting error scenarios where data
 * retrieval may fail.
 */
public class UserManagementContextBuilder {

  private final String type;
  private final User operator;
  private final OAuthToken oAuthToken;
  private final RequestAttributes requestAttributes;
  private final UserManagementRequest request;
  private final TenantIdentifier tenantIdentifier;
  private final boolean dryRun;

  private User before; // nullable: null in error scenarios
  private User after; // nullable: null in error scenarios

  public UserManagementContextBuilder(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      RequestAttributes requestAttributes,
      UserManagementRequest request,
      boolean dryRun) {
    this.type = "user";
    this.tenantIdentifier = tenantIdentifier;
    this.operator = operator;
    this.oAuthToken = oAuthToken;
    this.requestAttributes = requestAttributes;
    this.request = request;
    this.dryRun = dryRun;
  }

  public UserManagementContextBuilder withBefore(User before) {
    this.before = before;
    return this;
  }

  public UserManagementContextBuilder withAfter(User after) {
    this.after = after;
    return this;
  }

  /**
   * Builds complete UserUpdateContext.
   *
   * @return full context with before/after users
   * @throws IllegalStateException if required data is missing
   */
  public AuditableContext build() {

    return new UserManagementContext(
        type,
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
   * Builds partial UserUpdateContext for error scenarios.
   *
   * @param exception the exception that caused the failure
   * @return partial context with error information
   */
  public AuditableContext buildPartial(ManagementApiException exception) {
    // Use available data, even if incomplete
    return new UserManagementContext(
        type,
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
