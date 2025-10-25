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

package org.idp.server.control_plane.management.tenant;

import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.tenant.io.TenantManagementRequest;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;

public class TenantManagementContextBuilder {

  private final User operator;
  private final OAuthToken oAuthToken;
  private final RequestAttributes requestAttributes;
  private final TenantManagementRequest request;
  private final boolean dryRun;

  private TenantIdentifier tenantIdentifier = new TenantIdentifier();
  private Tenant before; // nullable: null in error scenarios
  private Tenant after; // nullable: null in error scenarios

  public TenantManagementContextBuilder(
      User operator,
      OAuthToken oAuthToken,
      RequestAttributes requestAttributes,
      TenantManagementRequest request,
      boolean dryRun) {
    this.operator = operator;
    this.oAuthToken = oAuthToken;
    this.requestAttributes = requestAttributes;
    this.request = request;
    this.dryRun = dryRun;
  }

  public TenantManagementContextBuilder withTargetTenantIdentifier(
      TenantIdentifier tenantIdentifier) {
    this.tenantIdentifier = tenantIdentifier;
    return this;
  }

  public TenantManagementContextBuilder withBefore(Tenant before) {
    this.before = before;
    return this;
  }

  public TenantManagementContextBuilder withAfter(Tenant after) {
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

    return new TenantManagementContext(
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
    return new TenantManagementContext(
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
