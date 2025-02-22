package org.idp.server.handler.oauth;

import java.util.Objects;

import org.idp.server.basic.date.SystemDateTime;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ClientConfigurationRepository;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.configuration.ServerConfigurationRepository;
import org.idp.server.handler.oauth.io.OAuthRequest;
import org.idp.server.handler.oauth.io.OAuthRequestResponse;
import org.idp.server.handler.oauth.io.OAuthRequestStatus;
import org.idp.server.oauth.*;
import org.idp.server.oauth.authentication.Authentication;
import org.idp.server.oauth.exception.OAuthRedirectableBadRequestException;
import org.idp.server.oauth.gateway.RequestObjectGateway;
import org.idp.server.oauth.identity.User;
import org.idp.server.oauth.repository.AuthorizationRequestRepository;
import org.idp.server.oauth.request.OAuthRequestParameters;
import org.idp.server.oauth.service.*;
import org.idp.server.oauth.validator.OAuthRequestValidator;
import org.idp.server.oauth.verifier.OAuthRequestVerifier;
import org.idp.server.type.oauth.TokenIssuer;

/** OAuthRequestHandler */
public class OAuthRequestHandler {

  OAuthRequestContextServices oAuthRequestContextServices;
  OAuthRequestVerifier verifier;
  AuthorizationRequestRepository authorizationRequestRepository;
  ServerConfigurationRepository serverConfigurationRepository;
  ClientConfigurationRepository clientConfigurationRepository;

  public OAuthRequestHandler(
      AuthorizationRequestRepository authorizationRequestRepository,
      ServerConfigurationRepository serverConfigurationRepository,
      ClientConfigurationRepository clientConfigurationRepository,
      RequestObjectGateway requestObjectGateway) {
    this.oAuthRequestContextServices = new OAuthRequestContextServices(requestObjectGateway);
    this.verifier = new OAuthRequestVerifier();
    this.authorizationRequestRepository = authorizationRequestRepository;
    this.serverConfigurationRepository = serverConfigurationRepository;
    this.clientConfigurationRepository = clientConfigurationRepository;
  }

  public OAuthRequestContext handle(OAuthRequest oAuthRequest, OAuthRequestDelegate delegate) {
    OAuthRequestParameters parameters = oAuthRequest.toParameters();
    TokenIssuer tokenIssuer = oAuthRequest.toTokenIssuer();
    OAuthRequestValidator validator = new OAuthRequestValidator(parameters);
    validator.validate();

    ServerConfiguration serverConfiguration = serverConfigurationRepository.get(tokenIssuer);
    ClientConfiguration clientConfiguration =
        clientConfigurationRepository.get(tokenIssuer, parameters.clientId());

    OAuthRequestAnalyzer analyzer = new OAuthRequestAnalyzer(parameters);
    OAuthRequestPattern oAuthRequestPattern = analyzer.analyzePattern();
    OAuthRequestContextService oAuthRequestContextService =
        oAuthRequestContextServices.get(oAuthRequestPattern);

    OAuthRequestContext context =
        oAuthRequestContextService.create(parameters, serverConfiguration, clientConfiguration);
    verifier.verify(context);
    authorizationRequestRepository.register(context.authorizationRequest());

    if (Objects.nonNull(delegate)) {
      OAuthSessionKey oAuthSessionKey = new OAuthSessionKey(tokenIssuer.value(), parameters.clientId().value());
      OAuthSession session =
              new OAuthSession(
                      oAuthSessionKey, new User(), new Authentication(), SystemDateTime.now().plusSeconds(3600));
      delegate.registerSession(session);
    }

    return context;
  }

  public boolean isAuthorizable(
      OAuthRequestContext context,
      OAuthSession session,
      OAuthRequestDelegate oAuthRequestDelegate) {
    if (!context.isPromptNone()) {
      return false;
    }
    if (Objects.isNull(oAuthRequestDelegate)) {
      throw new OAuthRedirectableBadRequestException(
          "login_required", "invalid session, session registration function is disable", context);
    }
    if (Objects.isNull(session) || !session.exists()) {
      throw new OAuthRedirectableBadRequestException(
          "login_required", "invalid session, session is not registered", context);
    }
    if (!session.isValid(context.authorizationRequest())) {
      throw new OAuthRedirectableBadRequestException(
          "login_required", "invalid session, session is invalid", context);
    }
    return true;
  }

  public OAuthRequestResponse handleResponse(OAuthRequestContext context, OAuthSession session) {
    if (context.isPromptCreate()) {
      return new OAuthRequestResponse(OAuthRequestStatus.OK_ACCOUNT_CREATION, context, session);
    }
    if (Objects.isNull(session) || !session.exists()) {
      return new OAuthRequestResponse(OAuthRequestStatus.OK, context, session);
    }
    if (!session.isValid(context.authorizationRequest())) {
      return new OAuthRequestResponse(OAuthRequestStatus.OK, context, session);
    }
    return new OAuthRequestResponse(OAuthRequestStatus.OK_SESSION_ENABLE, context, session);
  }
}
