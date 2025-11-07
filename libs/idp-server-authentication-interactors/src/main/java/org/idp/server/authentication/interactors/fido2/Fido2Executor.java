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

package org.idp.server.authentication.interactors.fido2;

import org.idp.server.core.openid.authentication.AuthenticationInteractionRequest;
import org.idp.server.core.openid.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface Fido2Executor {

  Fido2ExecutorType type();

  Fido2Challenge challengeRegistration(
      Tenant tenant,
      AuthenticationTransactionIdentifier authenticationTransactionIdentifier,
      AuthenticationInteractionRequest request,
      Fido2Configuration configuration);

  Fido2VerificationResult verifyRegistration(
      Tenant tenant,
      AuthenticationTransactionIdentifier authenticationTransactionIdentifier,
      String userId,
      AuthenticationInteractionRequest request,
      Fido2Configuration configuration);

  Fido2Challenge challengeAuthentication(
      Tenant tenant,
      AuthenticationTransactionIdentifier authenticationTransactionIdentifier,
      AuthenticationInteractionRequest request,
      Fido2Configuration configuration);

  Fido2VerificationResult verifyAuthentication(
      Tenant tenant,
      AuthenticationTransactionIdentifier authenticationTransactionIdentifier,
      AuthenticationInteractionRequest request,
      Fido2Configuration configuration);
}
