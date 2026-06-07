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

import java.util.LinkedHashMap;
import java.util.Map;
import org.idp.server.control_plane.admin.operation.IdpServerOperationApi;
import org.idp.server.control_plane.admin.operation.io.IdpServerOperationRequest;
import org.idp.server.control_plane.admin.operation.io.IdpServerOperationResponse;
import org.idp.server.control_plane.admin.operation.io.IdpServerOperationStatus;
import org.idp.server.core.extension.ciba.repository.BackchannelAuthenticationRequestOperationCommandRepository;
import org.idp.server.core.extension.ciba.repository.CibaGrantOperationCommandRepository;
import org.idp.server.core.openid.authentication.repository.AuthenticationTransactionOperationCommandRepository;
import org.idp.server.core.openid.federation.sso.SsoSessionCommandRepository;
import org.idp.server.core.openid.oauth.repository.AuthorizationCodeGrantOperationCommandRepository;
import org.idp.server.core.openid.oauth.repository.AuthorizationRequestOperationCommandRepository;
import org.idp.server.core.openid.token.repository.OAuthTokenOperationCommandRepository;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class IdpServerOperationEntryService implements IdpServerOperationApi {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(IdpServerOperationEntryService.class);

  TenantQueryRepository tenantQueryRepository;
  OAuthTokenOperationCommandRepository oAuthTokenOperationCommandRepository;
  AuthenticationTransactionOperationCommandRepository
      authenticationTransactionOperationCommandRepository;
  AuthorizationRequestOperationCommandRepository authorizationRequestOperationCommandRepository;
  AuthorizationCodeGrantOperationCommandRepository authorizationCodeGrantOperationCommandRepository;
  BackchannelAuthenticationRequestOperationCommandRepository
      backchannelAuthenticationRequestOperationCommandRepository;
  CibaGrantOperationCommandRepository cibaGrantOperationCommandRepository;
  SsoSessionCommandRepository ssoSessionCommandRepository;

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
      CibaGrantOperationCommandRepository cibaGrantOperationCommandRepository,
      SsoSessionCommandRepository ssoSessionCommandRepository) {
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
    this.ssoSessionCommandRepository = ssoSessionCommandRepository;
  }

  @Override
  public IdpServerOperationResponse deleteExpiredData(
      TenantIdentifier adminTenantIdentifier,
      IdpServerOperationRequest request,
      RequestAttributes requestAttributes) {

    Tenant adminTenant = tenantQueryRepository.get(adminTenantIdentifier);
    int maxDeletionNumber = request.optValueAsInt("max_deletion_number", 10000);

    // LinkedHashMap で API レスポンス上の順序を保つ。
    Map<String, Integer> deleted = new LinkedHashMap<>();
    deleted.put(
        "oauth_token",
        oAuthTokenOperationCommandRepository.deleteExpiredToken(adminTenant, maxDeletionNumber));
    deleted.put(
        "authentication_transaction",
        authenticationTransactionOperationCommandRepository.deleteExpiredTransaction(
            adminTenant, maxDeletionNumber));
    deleted.put(
        "authorization_request",
        authorizationRequestOperationCommandRepository.deleteExpiredRequest(
            adminTenant, maxDeletionNumber));
    deleted.put(
        "authorization_code_grant",
        authorizationCodeGrantOperationCommandRepository.deleteExpiredCodeGrant(
            adminTenant, maxDeletionNumber));
    deleted.put(
        "backchannel_authentication_request",
        backchannelAuthenticationRequestOperationCommandRepository.deleteExpiredRequest(
            adminTenant, maxDeletionNumber));
    deleted.put(
        "ciba_grant",
        cibaGrantOperationCommandRepository.deleteExpiredGrant(adminTenant, maxDeletionNumber));
    deleted.put(
        "federation_sso_session",
        ssoSessionCommandRepository.deleteExpired(adminTenant, maxDeletionNumber));

    int total = deleted.values().stream().mapToInt(Integer::intValue).sum();
    log.info(
        "deleteExpiredData completed. max_deletion_number={}, total_deleted={}, breakdown={}",
        maxDeletionNumber,
        total,
        deleted);

    return new IdpServerOperationResponse(
        IdpServerOperationStatus.OK, Map.of("deleted", deleted, "total_deleted", total));
  }
}
