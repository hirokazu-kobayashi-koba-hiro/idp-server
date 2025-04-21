package org.idp.server.core;

import java.util.Map;
import org.idp.server.core.authentication.*;
import org.idp.server.core.basic.datasource.Transaction;
import org.idp.server.core.ciba.*;
import org.idp.server.core.ciba.exception.BackchannelAuthenticationBadRequestException;
import org.idp.server.core.ciba.handler.io.*;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.identity.UserRepository;
import org.idp.server.core.security.event.CibaFlowEventPublisher;
import org.idp.server.core.security.event.DefaultSecurityEventType;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.tenant.TenantRepository;
import org.idp.server.core.type.security.RequestAttributes;

@Transaction
public class CibaFlowEntryService implements CibaFlowApi {

  CibaProtocols cibaProtocols;
  AuthenticationInteractors authenticationInteractors;
  UserRepository userRepository;
  TenantRepository tenantRepository;
  AuthenticationTransactionCommandRepository authenticationTransactionCommandRepository;
  AuthenticationTransactionQueryRepository authenticationTransactionQueryRepository;
  CibaFlowEventPublisher eventPublisher;

  public CibaFlowEntryService(
      CibaProtocols cibaProtocols,
      AuthenticationInteractors authenticationInteractors,
      UserRepository userRepository,
      TenantRepository tenantRepository,
      AuthenticationTransactionCommandRepository authenticationTransactionCommandRepository,
      AuthenticationTransactionQueryRepository authenticationTransactionQueryRepository,
      CibaFlowEventPublisher eventPublisher) {
    this.cibaProtocols = cibaProtocols;
    this.authenticationInteractors = authenticationInteractors;
    this.userRepository = userRepository;
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

    // TODO consider logic on provider-id
    User user =
        userRepository.findBy(tenant, requestResult.request().loginHint().value(), "idp-server");

    if (!user.exists()) {
      eventPublisher.publish(
          tenant,
          requestResult.request(),
          user,
          DefaultSecurityEventType.backchannel_authentication_request_success,
          requestAttributes);

      throw new BackchannelAuthenticationBadRequestException(
          "unknown_user_id",
          "The OpenID Provider is not able to identify which end-user the Client wishes to be authenticated by means of the hint provided in the request (login_hint_token, id_token_hint, or login_hint).");
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
            userRepository);

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
        new AuthorizationIdentifier(backchannelAuthenticationRequestIdentifier);

    AuthenticationTransaction authenticationTransaction =
        authenticationTransactionQueryRepository.get(tenant, authorizationIdentifier);
    AuthenticationInteractionRequestResult result =
        authenticationInteractor.interact(
            tenant,
            authorizationIdentifier,
            type,
            request,
            authenticationTransaction,
            userRepository);

    eventPublisher.publish(
        tenant,
        backchannelAuthenticationRequest,
        result.user(),
        result.eventType(),
        requestAttributes);

    return result;
  }

  public CibaAuthorizeResponse authorize(
      TenantIdentifier tenantIdentifier, String authReqId, RequestAttributes requestAttributes) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    CibaAuthorizeRequest cibaAuthorizeRequest = new CibaAuthorizeRequest(tenant, authReqId);

    CibaProtocol cibaProtocol = cibaProtocols.get(tenant.authorizationProtocolProvider());

    return cibaProtocol.authorize(cibaAuthorizeRequest);
  }

  public CibaDenyResponse deny(
      TenantIdentifier tenantIdentifier, String authReqId, RequestAttributes requestAttributes) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    CibaDenyRequest cibaDenyRequest = new CibaDenyRequest(tenant, authReqId);

    CibaProtocol cibaProtocol = cibaProtocols.get(tenant.authorizationProtocolProvider());

    return cibaProtocol.deny(cibaDenyRequest);
  }
}
