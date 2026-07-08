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

package org.idp.server.core.openid.identity;

import org.idp.server.core.openid.authentication.AuthenticationInteractionRequest;
import org.idp.server.core.openid.authentication.AuthenticationInteractionRequestResult;
import org.idp.server.core.openid.authentication.AuthenticationInteractionType;
import org.idp.server.core.openid.authentication.AuthenticationTransaction;
import org.idp.server.core.openid.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.openid.identity.authentication.PasswordChangeRequest;
import org.idp.server.core.openid.identity.authentication.PasswordChangeResponse;
import org.idp.server.core.openid.identity.authentication.PasswordResetRequest;
import org.idp.server.core.openid.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.core.openid.identity.io.AuthenticationDevicePatchRequest;
import org.idp.server.core.openid.identity.io.MfaRegistrationRequest;
import org.idp.server.core.openid.identity.io.UserOperationResponse;
import org.idp.server.core.openid.oauth.type.AuthFlow;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
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

  /**
   * Creates a self-service email-change transaction (Issue #1416).
   *
   * <p>Shares the transaction model with {@link #requestMfaOperation} (a {@code $.user}-bound
   * transaction driven via the standard interaction endpoint), but is exposed under its own {@code
   * /v1/me/email/change} entry so the API surface reflects the domain operation rather than the
   * internal reuse.
   */
  UserOperationResponse requestEmailConfirm(
      TenantIdentifier tenantIdentifier,
      User user,
      OAuthToken token,
      MfaRegistrationRequest request,
      RequestAttributes requestAttributes);

  /**
   * Verifies the emailed code and commits the new email for the {@link #requestEmailConfirm} flow.
   *
   * <p>The transaction must belong to {@code user}; otherwise it is treated as not found so one
   * authenticated user cannot drive another's change transaction by id.
   */
  UserOperationResponse verifyEmailConfirm(
      TenantIdentifier tenantIdentifier,
      User user,
      AuthenticationTransactionIdentifier authenticationTransactionIdentifier,
      AuthenticationInteractionRequest request,
      RequestAttributes requestAttributes);

  AuthenticationInteractionRequestResult interact(
      TenantIdentifier tenantIdentifier,
      AuthenticationTransactionIdentifier authenticationTransactionIdentifier,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      RequestAttributes requestAttributes);

  /**
   * Variant of {@link #interact} that accepts an already-locked {@link AuthenticationTransaction}.
   *
   * <p>Intended for the consolidated dispatcher in {@code AuthenticationTransactionEntryService} to
   * avoid redundant {@code tenantQueryRepository.get()} and {@code getForUpdate()} calls. Invoke
   * via the {@link org.idp.server.usecases.IdpServerApplication#rawUserOperationApi() raw
   * (non-proxied)} accessor so it joins the caller's transaction.
   */
  AuthenticationInteractionRequestResult interactInternal(
      Tenant tenant,
      AuthenticationTransaction lockedTransaction,
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

  UserOperationResponse deleteAuthenticationDevice(
      TenantIdentifier tenantIdentifier,
      User user,
      OAuthToken oAuthToken,
      AuthenticationDeviceIdentifier authenticationDeviceIdentifier,
      RequestAttributes requestAttributes);

  UserOperationResponse delete(
      TenantIdentifier tenantIdentifier,
      User user,
      OAuthToken oAuthToken,
      RequestAttributes requestAttributes);

  PasswordChangeResponse changePassword(
      TenantIdentifier tenantIdentifier,
      User user,
      OAuthToken oAuthToken,
      PasswordChangeRequest request,
      RequestAttributes requestAttributes);

  PasswordChangeResponse resetPassword(
      TenantIdentifier tenantIdentifier,
      User user,
      OAuthToken oAuthToken,
      PasswordResetRequest request,
      RequestAttributes requestAttributes);
}
