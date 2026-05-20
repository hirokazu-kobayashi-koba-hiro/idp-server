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

package org.idp.server.core.openid.authentication;

import org.idp.server.core.openid.authentication.io.AuthenticationTransactionFindingResponse;
import org.idp.server.core.openid.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;

public interface AuthenticationTransactionApi {

  AuthenticationTransaction get(
      TenantIdentifier tenantIdentifier,
      AuthenticationTransactionIdentifier authenticationTransactionIdentifier);

  AuthenticationTransactionFindingResponse findList(
      TenantIdentifier tenantIdentifier,
      AuthenticationDeviceIdentifier authenticationDeviceIdentifier,
      String authorizationHeader,
      AuthenticationTransactionQueries queries,
      RequestAttributes requestAttributes);

  /**
   * Consolidated entry point for authentication interaction across all flows (OAuth, CIBA, user
   * operation). Performs the tenant lookup and pessimistic lock acquisition once, then dispatches
   * to the appropriate flow-specific handler (via the raw, non-proxied API) so the entire
   * interaction runs in a single transaction.
   *
   * <p>This avoids the duplicate transaction / duplicate {@code set_config} pattern that occurs
   * when the controller pre-fetches the transaction and the downstream entry service re-acquires it
   * with {@code getForUpdate()}.
   */
  AuthenticationInteractionRequestResult interact(
      TenantIdentifier tenantIdentifier,
      AuthenticationTransactionIdentifier authenticationTransactionIdentifier,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      RequestAttributes requestAttributes);
}
