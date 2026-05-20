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

package org.idp.server.core.extension.ciba;

import java.util.Map;
import org.idp.server.core.extension.ciba.handler.io.CibaRequestResponse;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.core.openid.authentication.AuthenticationInteractionRequest;
import org.idp.server.core.openid.authentication.AuthenticationInteractionRequestResult;
import org.idp.server.core.openid.authentication.AuthenticationInteractionType;
import org.idp.server.core.openid.authentication.AuthenticationTransaction;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;

public interface CibaFlowApi {
  CibaRequestResponse request(
      TenantIdentifier tenantIdentifier,
      Map<String, String[]> params,
      String authorizationHeader,
      String clientCert,
      RequestAttributes requestAttributes);

  AuthenticationInteractionRequestResult interact(
      TenantIdentifier tenantIdentifier,
      BackchannelAuthenticationRequestIdentifier backchannelAuthenticationRequestIdentifier,
      AuthenticationTransaction authenticationTransaction,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      RequestAttributes requestAttributes);

  /**
   * Executes a CIBA interact operation with a pre-fetched {@link Tenant} and an already
   * pessimistically locked {@link AuthenticationTransaction}.
   *
   * <p>This variant is intended to be called from within an existing {@code @Transaction}
   * (typically the orchestrating {@code AuthenticationTransactionEntryService.interact()}), via the
   * {@link org.idp.server.usecases.IdpServerApplication#rawCibaFlowApi() raw (non-proxied)}
   * accessor to avoid opening a nested transaction.
   *
   * <p>By skipping the redundant {@code tenantQueryRepository.get()} and {@code
   * authenticationTransactionQueryRepository.getForUpdate()} that the standard {@link
   * #interact(TenantIdentifier, BackchannelAuthenticationRequestIdentifier,
   * AuthenticationTransaction, AuthenticationInteractionType, AuthenticationInteractionRequest,
   * RequestAttributes) interact} would do, this method reduces transactions, {@code set_config()}
   * calls, and DB connection acquisitions per request.
   *
   * @param tenant pre-resolved tenant
   * @param lockedTransaction authentication transaction already obtained with {@code
   *     getForUpdate()} in the caller's transaction
   */
  AuthenticationInteractionRequestResult interactInternal(
      Tenant tenant,
      AuthenticationTransaction lockedTransaction,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      RequestAttributes requestAttributes);
}
