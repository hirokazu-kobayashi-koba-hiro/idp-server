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

package org.idp.server.control_plane.management.identity.verification.application;

import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.identity.verification.application.io.IdentityVerificationApplicationManagementRequest;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplication;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;

public class IdentityVerificationApplicationManagementContextBuilder {

  private final User operator;
  private final OAuthToken oAuthToken;
  private final RequestAttributes requestAttributes;
  private final IdentityVerificationApplicationManagementRequest request;
  private final TenantIdentifier tenantIdentifier;
  private final boolean dryRun;

  private IdentityVerificationApplication before;

  public IdentityVerificationApplicationManagementContextBuilder(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      RequestAttributes requestAttributes,
      IdentityVerificationApplicationManagementRequest request,
      boolean dryRun) {
    this.tenantIdentifier = tenantIdentifier;
    this.operator = operator;
    this.oAuthToken = oAuthToken;
    this.requestAttributes = requestAttributes;
    this.request = request;
    this.dryRun = dryRun;
  }

  public IdentityVerificationApplicationManagementContextBuilder withBefore(
      IdentityVerificationApplication before) {
    this.before = before;
    return this;
  }

  public AuditableContext build() {
    return new IdentityVerificationApplicationManagementContext(
        tenantIdentifier, operator, oAuthToken, requestAttributes, before, request, dryRun, null);
  }

  public AuditableContext buildPartial(ManagementApiException exception) {
    return new IdentityVerificationApplicationManagementContext(
        tenantIdentifier,
        operator,
        oAuthToken,
        requestAttributes,
        before,
        request,
        dryRun,
        exception);
  }
}
