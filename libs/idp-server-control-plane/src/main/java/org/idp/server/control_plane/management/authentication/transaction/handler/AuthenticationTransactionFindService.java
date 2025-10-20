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

package org.idp.server.control_plane.management.authentication.transaction.handler;

import org.idp.server.control_plane.management.authentication.transaction.io.AuthenticationTransactionManagementResponse;
import org.idp.server.control_plane.management.authentication.transaction.io.AuthenticationTransactionManagementStatus;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.core.openid.authentication.AuthenticationTransaction;
import org.idp.server.core.openid.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.openid.authentication.repository.AuthenticationTransactionQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for finding a single authentication transaction.
 *
 * <p>Handles authentication transaction retrieval by identifier. Throws ResourceNotFoundException
 * if the transaction does not exist.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Transaction retrieval by identifier
 *   <li>Existence validation
 *   <li>Response formatting
 * </ul>
 */
public class AuthenticationTransactionFindService
    implements AuthenticationTransactionManagementService<AuthenticationTransactionIdentifier> {

  private final AuthenticationTransactionQueryRepository authenticationTransactionQueryRepository;

  public AuthenticationTransactionFindService(
      AuthenticationTransactionQueryRepository authenticationTransactionQueryRepository) {
    this.authenticationTransactionQueryRepository = authenticationTransactionQueryRepository;
  }

  @Override
  public AuthenticationTransactionManagementResult execute(
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationTransactionIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AuthenticationTransaction authenticationTransaction =
        authenticationTransactionQueryRepository.find(tenant, identifier);

    if (!authenticationTransaction.exists()) {
      throw new ResourceNotFoundException(
          String.format("Authentication transaction %s not found", identifier.value()));
    }

    AuthenticationTransactionManagementResponse managementResponse =
        new AuthenticationTransactionManagementResponse(
            AuthenticationTransactionManagementStatus.OK, authenticationTransaction.toRequestMap());
    return AuthenticationTransactionManagementResult.success(
        tenant.identifier(), managementResponse);
  }
}
