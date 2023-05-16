package org.idp.server.handler.oauth;

import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.handler.oauth.io.OAuthDenyRequest;
import org.idp.server.handler.oauth.io.OAuthRequest;
import org.idp.server.oauth.*;
import org.idp.server.oauth.gateway.RequestObjectGateway;
import org.idp.server.oauth.repository.AuthorizationRequestRepository;
import org.idp.server.oauth.repository.ClientConfigurationRepository;
import org.idp.server.oauth.repository.ServerConfigurationRepository;
import org.idp.server.oauth.service.*;
import org.idp.server.oauth.validator.OAuthRequestValidator;
import org.idp.server.oauth.verifier.OAuthRequestVerifier;
import org.idp.server.type.oauth.TokenIssuer;

import java.util.Objects;

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

  public OAuthRequestContext handle(OAuthRequest oAuthRequest) {
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

    return context;
  }

  public boolean isAuthorizable(OAuthRequest oAuthRequest, OAuthRequestContext context, OAuthRequestDelegate oAuthRequestDelegate) {
    return context.isPromptNone()
            && Objects.nonNull(oAuthRequestDelegate)
            && oAuthRequestDelegate.isValidSession(
            oAuthRequest.toSessionIdentifier(),
            oAuthRequest.toTokenIssuer(),
            context.authorizationRequest());
  }
}
