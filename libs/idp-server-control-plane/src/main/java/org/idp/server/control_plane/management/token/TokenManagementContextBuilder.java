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

package org.idp.server.control_plane.management.token;

import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.token.io.TokenManagementRequest;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;

public class TokenManagementContextBuilder {

  private final TenantIdentifier tenantIdentifier;
  private final User operator;
  private final RequestAttributes requestAttributes;
  private final TokenManagementRequest request;
  private final boolean dryRun;

  private OAuthToken oAuthToken;

  public TokenManagementContextBuilder(
      TenantIdentifier tenantIdentifier,
      User operator,
      RequestAttributes requestAttributes,
      TokenManagementRequest request,
      boolean dryRun) {
    this.tenantIdentifier = tenantIdentifier;
    this.operator = operator;
    this.requestAttributes = requestAttributes;
    this.request = request;
    this.dryRun = dryRun;
  }

  public TokenManagementContextBuilder withOAuthToken(OAuthToken oAuthToken) {
    this.oAuthToken = oAuthToken;
    return this;
  }

  public AuditableContext build() {
    return new TokenManagementContext(
        tenantIdentifier, operator, oAuthToken, requestAttributes, request, dryRun, null);
  }

  public AuditableContext buildPartial(ManagementApiException exception) {
    return new TokenManagementContext(
        tenantIdentifier, operator, oAuthToken, requestAttributes, request, dryRun, exception);
  }
}
