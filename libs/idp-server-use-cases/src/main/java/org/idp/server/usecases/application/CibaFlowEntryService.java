package org.idp.server.usecases.application;

import java.util.Map;
import org.idp.server.basic.datasource.Transaction;
import org.idp.server.basic.type.oauth.Error;
import org.idp.server.basic.type.oauth.ErrorDescription;
import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.core.authentication.*;
import org.idp.server.core.authentication.repository.AuthenticationTransactionCommandRepository;
import org.idp.server.core.authentication.repository.AuthenticationTransactionQueryRepository;
import org.idp.server.core.ciba.*;
import org.idp.server.core.ciba.handler.io.*;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.core.ciba.response.BackchannelAuthenticationErrorResponse;
import org.idp.server.core.ciba.user.UserHintResolver;
import org.idp.server.core.ciba.user.UserHintResolvers;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.repository.UserQueryRepository;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.multi_tenancy.tenant.TenantRepository;
import org.idp.server.core.security.event.CibaFlowEventPublisher;
import org.idp.server.core.security.event.DefaultSecurityEventType;

@Transaction
public class CibaFlowEntryService implements CibaFlowApi {

  CibaProtocols cibaProtocols;
  UserHintResolvers userHintResolvers;
  AuthenticationInteractors authenticationInteractors;
  UserQueryRepository userQueryRepository;
  TenantRepository tenantRepository;
  AuthenticationTransactionCommandRepository authenticationTransactionCommandRepository;
  AuthenticationTransactionQueryRepository authenticationTransactionQueryRepository;
  CibaFlowEventPublisher eventPublisher;

  public CibaFlowEntryService(
      CibaProtocols cibaProtocols,
      AuthenticationInteractors authenticationInteractors,
      UserQueryRepository userQueryRepository,
      TenantRepository tenantRepository,
      AuthenticationTransactionCommandRepository authenticationTransactionCommandRepository,
      AuthenticationTransactionQueryRepository authenticationTransactionQueryRepository,
      CibaFlowEventPublisher eventPublisher) {
    this.cibaProtocols = cibaProtocols;
    this.userHintResolvers = new UserHintResolvers();
    this.authenticationInteractors = authenticationInteractors;
    this.userQueryRepository = userQueryRepository;
    this.tenantRepository = tenantRepository;
    this.authenticationTransactionCommandRepository = authenticationTransactionCommandRepository;
    this.authenticationTransactionQueryRepository = authenticationTransactionQueryRepository;
    this.eventPublisher = eventPublisher;
  }

  public CibaRequestResponse request(
      TenantIdentifier tenantIdentifier,
      Map<String, String[]> params,
      String authorizationHeader,
      String clientCert,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    CibaRequest cibaRequest = new CibaRequest(tenant, authorizationHeader, params);
    cibaRequest.setClientCert(clientCert);

    CibaProtocol cibaProtocol = cibaProtocols.get(tenant.authorizationProtocolProvider());

    CibaRequestResult requestResult = cibaProtocol.request(cibaRequest);

    if (!requestResult.isOK()) {
      return requestResult.toErrorResponse();
    }

    UserHintResolver userHintResolver = this.userHintResolvers.get(requestResult.userHintType());
    User user =
        userHintResolver.resolve(
            tenant,
            requestResult.userhint(),
            requestResult.userHintRelatedParams(),
            userQueryRepository);

    if (!user.exists()) {
      eventPublisher.publish(
          tenant,
          requestResult.request(),
          user,
          DefaultSecurityEventType.backchannel_authentication_request_success,
          requestAttributes);

      BackchannelAuthenticationErrorResponse errorResponse =
          new BackchannelAuthenticationErrorResponse(
              new Error("unknown_user_id"),
              new ErrorDescription(
                  "The OpenID Provider is not able to identify which end-user the Client wishes to be authenticated by means of the hint provided in the request (login_hint_token, id_token_hint, or login_hint)."));
      return new CibaRequestResponse(CibaRequestStatus.BAD_REQUEST, errorResponse);
    }

    CibaIssueResponse issueResponse =
        cibaProtocol.issueResponse(new CibaIssueRequest(tenant, requestResult.context(), user));

    eventPublisher.publish(
        tenant,
        issueResponse.request(),
        user,
        DefaultSecurityEventType.backchannel_authentication_request_success,
        requestAttributes);

    AuthenticationTransaction authenticationTransaction =
        AuthenticationTransaction.createOnCibaFlow(tenant, issueResponse);
    authenticationTransactionCommandRepository.register(tenant, authenticationTransaction);

    AuthenticationInteractionType authenticationInteractionType =
        StandardAuthenticationInteraction.AUTHENTICATION_DEVICE_NOTIFICATION.toType();
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
        authenticationTransaction.update(interactionRequestResult);
    authenticationTransactionCommandRepository.update(tenant, updatedTransaction);

    eventPublisher.publish(
        tenant,
        issueResponse.request(),
        user,
        interactionRequestResult.eventType(),
        requestAttributes);

    return issueResponse.toResponse();
  }

  public AuthenticationInteractionRequestResult interact(
      TenantIdentifier tenantIdentifier,
      BackchannelAuthenticationRequestIdentifier backchannelAuthenticationRequestIdentifier,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);

    CibaProtocol cibaProtocol = cibaProtocols.get(tenant.authorizationProtocolProvider());
    BackchannelAuthenticationRequest backchannelAuthenticationRequest =
        cibaProtocol.get(tenant, backchannelAuthenticationRequestIdentifier);

    AuthenticationInteractor authenticationInteractor = authenticationInteractors.get(type);
    AuthorizationIdentifier authorizationIdentifier =
        backchannelAuthenticationRequestIdentifier.toAuthorizationIdentifier();

    AuthenticationTransaction authenticationTransaction =
        authenticationTransactionQueryRepository.get(tenant, authorizationIdentifier);
    AuthenticationInteractionRequestResult result =
        authenticationInteractor.interact(
            tenant,
            authorizationIdentifier,
            type,
            request,
            authenticationTransaction,
            userQueryRepository);

    AuthenticationTransaction updatedTransaction = authenticationTransaction.update(result);
    authenticationTransactionCommandRepository.update(tenant, updatedTransaction);

    eventPublisher.publish(
        tenant,
        backchannelAuthenticationRequest,
        result.user(),
        result.eventType(),
        requestAttributes);

    if (authenticationTransaction.isComplete()) {
      CibaAuthorizeRequest cibaAuthorizeRequest =
          new CibaAuthorizeRequest(tenant, backchannelAuthenticationRequestIdentifier);
      cibaProtocol.authorize(cibaAuthorizeRequest);
      eventPublisher.publish(
          tenant,
          backchannelAuthenticationRequest,
          result.user(),
          DefaultSecurityEventType.backchannel_authentication_authorize,
          requestAttributes);
    }

    if (authenticationTransaction.isDeny()) {
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

    return result;
  }
}
