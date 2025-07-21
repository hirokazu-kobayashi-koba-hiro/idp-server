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

package org.idp.server.usecases.control_plane.system_administrator;

import java.util.Map;
import org.idp.server.control_plane.admin.operation.IdpServerOperationApi;
import org.idp.server.control_plane.admin.operation.io.IdpServerOperationRequest;
import org.idp.server.control_plane.admin.operation.io.IdpServerOperationResponse;
import org.idp.server.control_plane.admin.operation.io.IdpServerOperationStatus;
import org.idp.server.core.extension.ciba.repository.BackchannelAuthenticationRequestOperationCommandRepository;
import org.idp.server.core.extension.ciba.repository.CibaGrantOperationCommandRepository;
import org.idp.server.core.oidc.authentication.repository.AuthenticationTransactionOperationCommandRepository;
import org.idp.server.core.oidc.repository.AuthorizationCodeGrantOperationCommandRepository;
import org.idp.server.core.oidc.repository.AuthorizationRequestOperationCommandRepository;
import org.idp.server.core.oidc.token.repository.OAuthTokenOperationCommandRepository;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class IdpServerOperationEntryService implements IdpServerOperationApi {

  TenantQueryRepository tenantQueryRepository;
  OAuthTokenOperationCommandRepository oAuthTokenOperationCommandRepository;
  AuthenticationTransactionOperationCommandRepository
      authenticationTransactionOperationCommandRepository;
  AuthorizationRequestOperationCommandRepository authorizationRequestOperationCommandRepository;
  AuthorizationCodeGrantOperationCommandRepository authorizationCodeGrantOperationCommandRepository;
  BackchannelAuthenticationRequestOperationCommandRepository
      backchannelAuthenticationRequestOperationCommandRepository;
  CibaGrantOperationCommandRepository cibaGrantOperationCommandRepository;

  public IdpServerOperationEntryService(
      TenantQueryRepository tenantQueryRepository,
      OAuthTokenOperationCommandRepository oAuthTokenOperationCommandRepository,
      AuthenticationTransactionOperationCommandRepository
          authenticationTransactionOperationCommandRepository,
      AuthorizationRequestOperationCommandRepository authorizationRequestOperationCommandRepository,
      AuthorizationCodeGrantOperationCommandRepository
          authorizationCodeGrantOperationCommandRepository,
      BackchannelAuthenticationRequestOperationCommandRepository
          backchannelAuthenticationRequestOperationCommandRepository,
      CibaGrantOperationCommandRepository cibaGrantOperationCommandRepository) {
    this.tenantQueryRepository = tenantQueryRepository;
    this.oAuthTokenOperationCommandRepository = oAuthTokenOperationCommandRepository;
    this.authenticationTransactionOperationCommandRepository =
        authenticationTransactionOperationCommandRepository;
    this.authorizationRequestOperationCommandRepository =
        authorizationRequestOperationCommandRepository;
    this.authorizationCodeGrantOperationCommandRepository =
        authorizationCodeGrantOperationCommandRepository;
    this.backchannelAuthenticationRequestOperationCommandRepository =
        backchannelAuthenticationRequestOperationCommandRepository;
    this.cibaGrantOperationCommandRepository = cibaGrantOperationCommandRepository;
  }

  @Override
  public IdpServerOperationResponse deleteExpiredData(
      TenantIdentifier adminTenantIdentifier,
      IdpServerOperationRequest request,
      RequestAttributes requestAttributes) {

    Tenant adminTenant = tenantQueryRepository.get(adminTenantIdentifier);
    int maxDeletionNumber = request.optValueAsInt("max_deletion_number", 10000);
    oAuthTokenOperationCommandRepository.deleteExpiredToken(adminTenant, maxDeletionNumber);
    authenticationTransactionOperationCommandRepository.deleteExpiredTransaction(
        adminTenant, maxDeletionNumber);
    authenticationTransactionOperationCommandRepository.deleteExpiredTransaction(
        adminTenant, maxDeletionNumber);
    authorizationRequestOperationCommandRepository.deleteExpiredRequest(
        adminTenant, maxDeletionNumber);
    backchannelAuthenticationRequestOperationCommandRepository.deleteExpiredRequest(
        adminTenant, maxDeletionNumber);
    cibaGrantOperationCommandRepository.deleteExpiredGrant(adminTenant, maxDeletionNumber);

    return new IdpServerOperationResponse(IdpServerOperationStatus.OK, Map.of());
  }
}
