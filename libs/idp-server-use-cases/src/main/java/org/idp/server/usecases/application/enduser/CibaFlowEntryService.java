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

package org.idp.server.usecases.application.enduser;

import java.util.Map;
import org.idp.server.core.extension.ciba.*;
import org.idp.server.core.extension.ciba.handler.io.*;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.core.extension.ciba.user.UserHintResolvers;
import org.idp.server.core.extension.ciba.verifier.additional.CibaRequestAdditionalVerifiers;
import org.idp.server.core.oidc.authentication.*;
import org.idp.server.core.oidc.authentication.repository.AuthenticationTransactionCommandRepository;
import org.idp.server.core.oidc.authentication.repository.AuthenticationTransactionQueryRepository;
import org.idp.server.core.oidc.identity.authentication.PasswordVerificationDelegation;
import org.idp.server.core.oidc.identity.event.UserLifecycleEvent;
import org.idp.server.core.oidc.identity.event.UserLifecycleEventPublisher;
import org.idp.server.core.oidc.identity.event.UserLifecycleType;
import org.idp.server.core.oidc.identity.repository.UserQueryRepository;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.security.type.RequestAttributes;

@Transaction
public class CibaFlowEntryService implements CibaFlowApi {

  CibaProtocols cibaProtocols;
  UserHintResolvers userHintResolvers;
  AuthenticationInteractors authenticationInteractors;
  CibaRequestAdditionalVerifiers additionalVerifiers;
  UserQueryRepository userQueryRepository;
  TenantQueryRepository tenantQueryRepository;
  AuthenticationTransactionCommandRepository authenticationTransactionCommandRepository;
  AuthenticationTransactionQueryRepository authenticationTransactionQueryRepository;
  CibaFlowEventPublisher eventPublisher;
  UserLifecycleEventPublisher userLifecycleEventPublisher;

  public CibaFlowEntryService(
      CibaProtocols cibaProtocols,
      AuthenticationInteractors authenticationInteractors,
      UserQueryRepository userQueryRepository,
      TenantQueryRepository tenantQueryRepository,
      AuthenticationTransactionCommandRepository authenticationTransactionCommandRepository,
      AuthenticationTransactionQueryRepository authenticationTransactionQueryRepository,
      CibaFlowEventPublisher eventPublisher,
      UserLifecycleEventPublisher userLifecycleEventPublisher,
      PasswordVerificationDelegation passwordVerificationDelegation) {
    this.cibaProtocols = cibaProtocols;
    this.userHintResolvers = new UserHintResolvers();
    this.authenticationInteractors = authenticationInteractors;
    this.additionalVerifiers = new CibaRequestAdditionalVerifiers(passwordVerificationDelegation);
    this.userQueryRepository = userQueryRepository;
    this.tenantQueryRepository = tenantQueryRepository;
    this.authenticationTransactionCommandRepository = authenticationTransactionCommandRepository;
    this.authenticationTransactionQueryRepository = authenticationTransactionQueryRepository;
    this.eventPublisher = eventPublisher;
    this.userLifecycleEventPublisher = userLifecycleEventPublisher;
  }

  public CibaRequestResponse request(
      TenantIdentifier tenantIdentifier,
      Map<String, String[]> params,
      String authorizationHeader,
      String clientCert,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    CibaRequest cibaRequest = new CibaRequest(tenant, authorizationHeader, params);
    cibaRequest.setClientCert(clientCert);

    CibaProtocol cibaProtocol = cibaProtocols.get(tenant.authorizationProvider());
    CibaIssueResponse issueResponse =
        cibaProtocol.request(cibaRequest, userHintResolvers, additionalVerifiers);
    if (!issueResponse.isOK()) {
      return issueResponse.toErrorResponse();
    }

    eventPublisher.publish(
        tenant,
        issueResponse.request(),
        issueResponse.user(),
        DefaultSecurityEventType.backchannel_authentication_request_success,
        requestAttributes);

    AuthenticationTransaction authenticationTransaction =
        CibaAuthenticationTransactionCreator.create(tenant, issueResponse);
    authenticationTransactionCommandRepository.register(tenant, authenticationTransaction);

    AuthenticationInteractionType authenticationInteractionType =
        issueResponse.defaultCibaAuthenticationInteractionType();
    AuthenticationInteractor authenticationInteractor =
        authenticationInteractors.get(authenticationInteractionType);
    AuthenticationInteractionRequestResult interactionRequestResult =
        authenticationInteractor.interact(
            tenant,
            authenticationTransaction.identifier(),
            authenticationInteractionType,
            new AuthenticationInteractionRequest(Map.of()),
            authenticationTransaction,
            userQueryRepository);

    AuthenticationTransaction updatedTransaction =
        authenticationTransaction.updateWith(interactionRequestResult);
    authenticationTransactionCommandRepository.update(tenant, updatedTransaction);

    eventPublisher.publish(
        tenant,
        issueResponse.request(),
        issueResponse.user(),
        interactionRequestResult.eventType(),
        requestAttributes);

    return issueResponse.toResponse();
  }

  public AuthenticationInteractionRequestResult interact(
      TenantIdentifier tenantIdentifier,
      BackchannelAuthenticationRequestIdentifier backchannelAuthenticationRequestIdentifier,
      AuthenticationTransaction authenticationTransaction,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    CibaProtocol cibaProtocol = cibaProtocols.get(tenant.authorizationProvider());
    BackchannelAuthenticationRequest backchannelAuthenticationRequest =
        cibaProtocol.get(tenant, backchannelAuthenticationRequestIdentifier);

    AuthenticationInteractor authenticationInteractor = authenticationInteractors.get(type);

    AuthenticationInteractionRequestResult result =
        authenticationInteractor.interact(
            tenant,
            authenticationTransaction.identifier(),
            type,
            request,
            authenticationTransaction,
            userQueryRepository);

    AuthenticationTransaction updatedTransaction = authenticationTransaction.updateWith(result);
    authenticationTransactionCommandRepository.update(tenant, updatedTransaction);

    eventPublisher.publish(
        tenant,
        backchannelAuthenticationRequest,
        result.user(),
        result.eventType(),
        requestAttributes);

    if (updatedTransaction.isSuccess()) {
      Authentication authentication = updatedTransaction.authentication();
      CibaAuthorizeRequest cibaAuthorizeRequest =
          new CibaAuthorizeRequest(
              tenant, backchannelAuthenticationRequestIdentifier, authentication);
      cibaProtocol.authorize(cibaAuthorizeRequest);
      eventPublisher.publish(
          tenant,
          backchannelAuthenticationRequest,
          result.user(),
          DefaultSecurityEventType.backchannel_authentication_authorize,
          requestAttributes);
    }

    if (updatedTransaction.isFailure()) {
      CibaDenyRequest cibaDenyRequest =
          new CibaDenyRequest(tenant, backchannelAuthenticationRequestIdentifier);
      cibaProtocol.deny(cibaDenyRequest);
      eventPublisher.publish(
          tenant,
          backchannelAuthenticationRequest,
          result.user(),
          DefaultSecurityEventType.backchannel_authentication_deny,
          requestAttributes);
    }

    if (updatedTransaction.isLocked()) {
      UserLifecycleEvent userLifecycleEvent =
          new UserLifecycleEvent(tenant, result.user(), UserLifecycleType.LOCK);
      userLifecycleEventPublisher.publish(userLifecycleEvent);
    }

    return result;
  }
}
