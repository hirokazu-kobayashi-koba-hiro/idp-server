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
import java.util.Set;
import org.idp.server.account_linking.AccountLinkingTokenCipher;
import org.idp.server.account_linking.repository.AccountLinkingSessionOperationCommandRepository;
import org.idp.server.control_plane.admin.operation.IdpServerOperationApi;
import org.idp.server.control_plane.admin.operation.io.IdpServerOperationRequest;
import org.idp.server.control_plane.admin.operation.io.IdpServerOperationResponse;
import org.idp.server.control_plane.admin.operation.io.IdpServerOperationStatus;
import org.idp.server.core.extension.ciba.repository.BackchannelAuthenticationRequestOperationCommandRepository;
import org.idp.server.core.extension.ciba.repository.CibaGrantOperationCommandRepository;
import org.idp.server.core.openid.authentication.repository.AuthenticationTransactionOperationCommandRepository;
import org.idp.server.core.openid.clientinstance.registration.ClientInstanceRegistrationChallengeOperationCommandRepository;
import org.idp.server.core.openid.federation.sso.SsoSessionOperationCommandRepository;
import org.idp.server.core.openid.identity.contact.ContactVerificationChallengeOperationCommandRepository;
import org.idp.server.core.openid.oauth.repository.AuthorizationCodeGrantOperationCommandRepository;
import org.idp.server.core.openid.oauth.repository.AuthorizationRequestOperationCommandRepository;
import org.idp.server.core.openid.token.repository.OAuthTokenOperationCommandRepository;
import org.idp.server.platform.crypto.datakey.DataKeyService;
import org.idp.server.platform.crypto.datakey.DataKeyStatusReport;
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
  SsoSessionOperationCommandRepository ssoSessionOperationCommandRepository;
  ContactVerificationChallengeOperationCommandRepository
      contactVerificationChallengeOperationCommandRepository;
  ClientInstanceRegistrationChallengeOperationCommandRepository
      clientInstanceRegistrationChallengeOperationCommandRepository;
  AccountLinkingSessionOperationCommandRepository accountLinkingSessionOperationCommandRepository;
  DataKeyService dataKeyService;

  /** Purposes a data key may be rotated for through this API. */
  static final Set<String> DATA_KEY_PURPOSES = Set.of(AccountLinkingTokenCipher.PURPOSE);

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
      SsoSessionOperationCommandRepository ssoSessionOperationCommandRepository,
      ContactVerificationChallengeOperationCommandRepository
          contactVerificationChallengeOperationCommandRepository,
      ClientInstanceRegistrationChallengeOperationCommandRepository
          clientInstanceRegistrationChallengeOperationCommandRepository,
      AccountLinkingSessionOperationCommandRepository
          accountLinkingSessionOperationCommandRepository,
      DataKeyService dataKeyService) {
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
    this.ssoSessionOperationCommandRepository = ssoSessionOperationCommandRepository;
    this.contactVerificationChallengeOperationCommandRepository =
        contactVerificationChallengeOperationCommandRepository;
    this.clientInstanceRegistrationChallengeOperationCommandRepository =
        clientInstanceRegistrationChallengeOperationCommandRepository;
    this.accountLinkingSessionOperationCommandRepository =
        accountLinkingSessionOperationCommandRepository;
    this.dataKeyService = dataKeyService;
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
        ssoSessionOperationCommandRepository.deleteExpired(adminTenant, maxDeletionNumber));
    deleted.put(
        "contact_verification_challenge",
        contactVerificationChallengeOperationCommandRepository.deleteExpired(
            adminTenant, maxDeletionNumber));
    deleted.put(
        "client_instance_registration_challenge",
        clientInstanceRegistrationChallengeOperationCommandRepository.deleteExpired(
            adminTenant, maxDeletionNumber));
    deleted.put(
        "account_linking_session",
        accountLinkingSessionOperationCommandRepository.deleteExpired(
            adminTenant, maxDeletionNumber));

    int total = deleted.values().stream().mapToInt(Integer::intValue).sum();
    log.info(
        "deleteExpiredData completed. max_deletion_number={}, total_deleted={}, breakdown={}",
        maxDeletionNumber,
        total,
        deleted);

    return new IdpServerOperationResponse(
        IdpServerOperationStatus.OK, Map.of("deleted", deleted, "total_deleted", total));
  }

  @Override
  public IdpServerOperationResponse dataKeyStatus(
      TenantIdentifier adminTenantIdentifier, RequestAttributes requestAttributes) {

    Tenant adminTenant = tenantQueryRepository.get(adminTenantIdentifier);
    DataKeyStatusReport report = dataKeyService.report(adminTenant);
    return new IdpServerOperationResponse(IdpServerOperationStatus.OK, report.toMap());
  }

  @Override
  public IdpServerOperationResponse rewrapDataKeys(
      TenantIdentifier adminTenantIdentifier,
      IdpServerOperationRequest request,
      RequestAttributes requestAttributes) {

    Tenant adminTenant = tenantQueryRepository.get(adminTenantIdentifier);
    int maxNumber = request.optValueAsInt("max_number", 1000);

    int rewrapped = dataKeyService.rewrap(adminTenant, maxNumber);
    DataKeyStatusReport report = dataKeyService.report(adminTenant);
    log.info(
        "rewrapDataKeys completed. max_number={}, rewrapped={}, pending_rewrap={}",
        maxNumber,
        rewrapped,
        report.pendingRewrap());

    Map<String, Object> contents = new LinkedHashMap<>();
    contents.put("rewrapped", rewrapped);
    contents.put("pending_rewrap", report.pendingRewrap());
    contents.put("status", report.toMap());
    return new IdpServerOperationResponse(IdpServerOperationStatus.OK, contents);
  }

  @Override
  public IdpServerOperationResponse rotateDataKey(
      TenantIdentifier adminTenantIdentifier,
      IdpServerOperationRequest request,
      RequestAttributes requestAttributes) {

    Object tenantId = request.get("tenant_id");
    Object purposeValue = request.get("purpose");
    String purpose =
        purposeValue == null ? AccountLinkingTokenCipher.PURPOSE : purposeValue.toString();
    if (!(tenantId instanceof String) || ((String) tenantId).isBlank()) {
      return invalidRequest("tenant_id is required.");
    }
    if (!DATA_KEY_PURPOSES.contains(purpose)) {
      return invalidRequest("purpose must be one of " + DATA_KEY_PURPOSES + ".");
    }

    Tenant tenant = tenantQueryRepository.get(new TenantIdentifier((String) tenantId));
    String dataKeyId = dataKeyService.rotate(tenant, purpose);

    Map<String, Object> contents = new LinkedHashMap<>();
    contents.put("tenant_id", tenant.identifierValue());
    contents.put("purpose", purpose);
    contents.put("data_key_id", dataKeyId);
    return new IdpServerOperationResponse(IdpServerOperationStatus.OK, contents);
  }

  private IdpServerOperationResponse invalidRequest(String description) {
    return new IdpServerOperationResponse(
        IdpServerOperationStatus.INVALID_REQUEST,
        Map.of("error", "invalid_request", "error_description", description));
  }
}
