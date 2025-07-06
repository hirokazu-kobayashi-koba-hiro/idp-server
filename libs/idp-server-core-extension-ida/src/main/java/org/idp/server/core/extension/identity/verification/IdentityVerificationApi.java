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

package org.idp.server.core.extension.identity.verification;

import org.idp.server.core.extension.identity.verification.io.IdentityVerificationResponse;
import org.idp.server.core.extension.identity.verification.result.IdentityVerificationResultQueries;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.token.OAuthToken;
import org.idp.server.platform.http.BasicAuth;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.security.type.RequestAttributes;

public interface IdentityVerificationApi {

  IdentityVerificationResponse register(
      TenantIdentifier tenantIdentifier,
      BasicAuth basicAuth,
      IdentityVerificationType identityVerificationType,
      IdentityVerificationRequest request,
      RequestAttributes requestAttributes);

  IdentityVerificationResponse findList(
      TenantIdentifier tenantIdentifier,
      User user,
      OAuthToken oAuthToken,
      IdentityVerificationResultQueries queries,
      RequestAttributes requestAttributes);
}
