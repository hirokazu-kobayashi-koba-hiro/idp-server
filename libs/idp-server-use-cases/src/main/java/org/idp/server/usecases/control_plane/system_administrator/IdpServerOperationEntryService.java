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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.account_linking.repository.AccountLinkingSessionOperationCommandRepository;
import org.idp.server.control_plane.admin.operation.IdpServerOperationApi;
import org.idp.server.control_plane.admin.operation.io.IdpServerOperationRequest;
import org.idp.server.control_plane.admin.operation.io.IdpServerOperationResponse;
import org.idp.server.control_plane.admin.operation.io.IdpServerOperationStatus;
import org.idp.server.control_plane.management.tenant.DataKeyPurposes;
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
import org.idp.server.platform.crypto.datakey.TenantDataKey;
import org.idp.server.platform.crypto.datakey.TenantDataKeyQueries;
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

  static final int MAX_DELETION_NUMBER = 100000;
  static final int MAX_REWRAP_NUMBER = 10000;

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
    Integer maxDeletionNumber =
        request.optBoundedInt("max_deletion_number", 10000, 1, MAX_DELETION_NUMBER);
    if (maxDeletionNumber == null) {
      return invalidRequest(
          "max_deletion_number must be an integer from 1 to " + MAX_DELETION_NUMBER + ".");
    }

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
  public IdpServerOperationResponse dataKeyList(
      TenantIdentifier adminTenantIdentifier,
      Map<String, String> queries,
      RequestAttributes requestAttributes) {

    TenantDataKeyQueries dataKeyQueries = new TenantDataKeyQueries(queries);
    if (!dataKeyQueries.isTenantIdValid()) {
      return invalidRequest("tenant_id must be a UUID.");
    }
    if (!dataKeyQueries.isStatusValid()) {
      return invalidRequest("status must be one of active, passive, destroyed.");
    }

    Tenant adminTenant = tenantQueryRepository.get(adminTenantIdentifier);
    List<Map<String, Object>> list = new ArrayList<>();
    for (TenantDataKey key : dataKeyService.findList(adminTenant, dataKeyQueries)) {
      Map<String, Object> item = key.toMap();
      // Whether the answering server can open the key; false means its KEK is missing here.
      item.put("kek_configured", dataKeyService.holds(key.kekId()));
      list.add(item);
    }

    Map<String, Object> contents = new LinkedHashMap<>();
    contents.put("list", list);
    contents.put("total_count", dataKeyService.findTotalCount(adminTenant, dataKeyQueries));
    contents.put("limit", dataKeyQueries.limit());
    contents.put("offset", dataKeyQueries.offset());
    return new IdpServerOperationResponse(IdpServerOperationStatus.OK, contents);
  }

  @Override
  public IdpServerOperationResponse rewrapDataKeys(
      TenantIdentifier adminTenantIdentifier,
      IdpServerOperationRequest request,
      RequestAttributes requestAttributes) {

    Tenant adminTenant = tenantQueryRepository.get(adminTenantIdentifier);
    Integer maxNumber = request.optBoundedInt("max_number", 1000, 1, MAX_REWRAP_NUMBER);
    if (maxNumber == null) {
      return invalidRequest("max_number must be an integer from 1 to " + MAX_REWRAP_NUMBER + ".");
    }

    // The re-wrap uses the KEK the answering server holds as active. During a rolling update the
    // servers disagree, and an old one would wrap the data keys back under the old KEK. The caller
    // names the KEK it means, and a server that holds another one refuses.
    String expectedActiveKeyId = request.optString("expected_active_key_id");
    if (expectedActiveKeyId == null) {
      return invalidRequest("expected_active_key_id is required.");
    }
    DataKeyStatusReport before = dataKeyService.report(adminTenant);
    if (!before.activeKekId().equals(expectedActiveKeyId)) {
      Map<String, Object> conflict = new LinkedHashMap<>();
      conflict.put("error", "conflict");
      conflict.put(
          "error_description",
          "This server encrypts with another key encryption key. Finish rolling out the"
              + " configuration first.");
      conflict.put("active_key_id", before.activeKekId());
      conflict.put("expected_active_key_id", expectedActiveKeyId);
      return new IdpServerOperationResponse(IdpServerOperationStatus.CONFLICT, conflict);
    }

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

    String tenantId = request.optString("tenant_id");
    Object purposeValue = request.get("purpose");
    String purpose = purposeValue == null ? DataKeyPurposes.DEFAULT : purposeValue.toString();
    if (tenantId == null || !isUuid(tenantId)) {
      return invalidRequest("tenant_id is required and must be a UUID.");
    }
    if (!DataKeyPurposes.isKnown(purpose)) {
      return invalidRequest("purpose must be one of " + DataKeyPurposes.ALL + ".");
    }

    Tenant tenant = tenantQueryRepository.find(new TenantIdentifier(tenantId));
    if (!tenant.exists()) {
      return new IdpServerOperationResponse(
          IdpServerOperationStatus.NOT_FOUND,
          Map.of("error", "not_found", "error_description", "The tenant does not exist."));
    }
    String dataKeyId = dataKeyService.rotate(tenant, purpose);

    Map<String, Object> contents = new LinkedHashMap<>();
    contents.put("tenant_id", tenant.identifierValue());
    contents.put("purpose", purpose);
    contents.put("data_key_id", dataKeyId);
    return new IdpServerOperationResponse(IdpServerOperationStatus.OK, contents);
  }

  private static boolean isUuid(String value) {
    try {
      java.util.UUID.fromString(value);
      return true;
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }

  private IdpServerOperationResponse invalidRequest(String description) {
    return new IdpServerOperationResponse(
        IdpServerOperationStatus.INVALID_REQUEST,
        Map.of("error", "invalid_request", "error_description", description));
  }
}
