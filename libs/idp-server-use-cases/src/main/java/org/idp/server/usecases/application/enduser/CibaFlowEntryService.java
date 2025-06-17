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

import java.util.List;
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
import org.idp.server.platform.log.LoggerWrapper;
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
  LoggerWrapper log = LoggerWrapper.getLogger(this.getClass());

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

    long startAll = System.currentTimeMillis();

    long t1 = System.currentTimeMillis();
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    CibaRequest cibaRequest = new CibaRequest(tenant, authorizationHeader, params);
    cibaRequest.setClientCert(clientCert);
    log.info("[CIBA] step1: tenant + request create = {}ms", System.currentTimeMillis() - t1);

    long t2 = System.currentTimeMillis();
    CibaProtocol cibaProtocol = cibaProtocols.get(tenant.authorizationProvider());
    CibaIssueResponse issueResponse =
        cibaProtocol.request(cibaRequest, userHintResolvers, additionalVerifiers);
    log.info("[CIBA] step2: protocol.request = {}ms", System.currentTimeMillis() - t2);
    if (!issueResponse.isOK()) {
      return issueResponse.toErrorResponse();
    }

    long t3 = System.currentTimeMillis();
    eventPublisher.publish(
        tenant,
        issueResponse.request(),
        issueResponse.user(),
        DefaultSecurityEventType.backchannel_authentication_request_success,
        requestAttributes);
    log.info("[CIBA] step3: event publish(success) = {}ms", System.currentTimeMillis() - t3);

    long t4 = System.currentTimeMillis();
    AuthenticationTransaction authenticationTransaction =
        CibaAuthenticationTransactionCreator.create(tenant, issueResponse);
    log.info("[CIBA] step4: transaction create = {}ms", System.currentTimeMillis() - t4);

    long t5 = System.currentTimeMillis();
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
    log.info("[CIBA] step5: authentication interact = {}ms", System.currentTimeMillis() - t5);

    long t6 = System.currentTimeMillis();
    AuthenticationTransaction updatedTransaction =
        authenticationTransaction.updateWith(interactionRequestResult);
    authenticationTransactionCommandRepository.register(tenant, updatedTransaction);
    log.info("[CIBA] step6: transaction update = {}ms", System.currentTimeMillis() - t6);

    long t7 = System.currentTimeMillis();
    eventPublisher.publish(
        tenant,
        issueResponse.request(),
        issueResponse.user(),
        interactionRequestResult.eventType(),
        requestAttributes);
    log.info("[CIBA] step7: event publish(result) = {}ms", System.currentTimeMillis() - t7);

    log.info("[CIBA] total time = {}ms", System.currentTimeMillis() - startAll);
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

    eventPublisher.publish(
        tenant,
        backchannelAuthenticationRequest,
        result.user(),
        result.eventType(),
        requestAttributes);

    if (updatedTransaction.isSuccess()) {

      Authentication authentication = updatedTransaction.authentication();
      List<String> deniedScopes = updatedTransaction.deniedScopes();
      CibaAuthorizeRequest cibaAuthorizeRequest =
          new CibaAuthorizeRequest(
              tenant, backchannelAuthenticationRequestIdentifier, authentication);
      cibaAuthorizeRequest.setDeniedScopes(deniedScopes);

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

    if (updatedTransaction.isComplete()) {
      authenticationTransactionCommandRepository.delete(
          tenant, authenticationTransaction.identifier());
    } else {
      authenticationTransactionCommandRepository.update(tenant, authenticationTransaction);
    }

    return result;
  }
}
