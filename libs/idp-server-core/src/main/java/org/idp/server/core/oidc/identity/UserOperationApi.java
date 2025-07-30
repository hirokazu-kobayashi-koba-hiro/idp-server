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

package org.idp.server.core.oidc.identity;

import org.idp.server.core.oidc.authentication.AuthenticationInteractionRequest;
import org.idp.server.core.oidc.authentication.AuthenticationInteractionRequestResult;
import org.idp.server.core.oidc.authentication.AuthenticationInteractionType;
import org.idp.server.core.oidc.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.oidc.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.core.oidc.identity.io.AuthenticationDevicePatchRequest;
import org.idp.server.core.oidc.identity.io.MfaRegistrationRequest;
import org.idp.server.core.oidc.identity.io.UserOperationResponse;
import org.idp.server.core.oidc.token.OAuthToken;
import org.idp.server.core.oidc.type.AuthFlow;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;

public interface UserOperationApi {

  UserOperationResponse requestMfaOperation(
      TenantIdentifier tenantIdentifier,
      User user,
      OAuthToken token,
      AuthFlow authFlow,
      MfaRegistrationRequest request,
      RequestAttributes requestAttributes);

  AuthenticationInteractionRequestResult interact(
      TenantIdentifier tenantIdentifier,
      AuthenticationTransactionIdentifier authenticationTransactionIdentifier,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      RequestAttributes requestAttributes);

  UserOperationResponse patchAuthenticationDevice(
      TenantIdentifier tenantIdentifier,
      User user,
      OAuthToken oAuthToken,
      AuthenticationDeviceIdentifier authenticationDeviceIdentifier,
      AuthenticationDevicePatchRequest request,
      RequestAttributes requestAttributes);

  void delete(
      TenantIdentifier tenantIdentifier,
      User user,
      OAuthToken oAuthToken,
      RequestAttributes requestAttributes);
}
