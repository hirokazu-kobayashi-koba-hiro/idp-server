package org.idp.server.handler.oauth;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.handler.io.OAuthRequest;
import org.idp.server.oauth.OAuthRequestAnalyzer;
import org.idp.server.oauth.OAuthRequestContext;
import org.idp.server.oauth.OAuthRequestPattern;
import org.idp.server.oauth.gateway.RequestObjectGateway;
import org.idp.server.oauth.repository.AuthorizationRequestRepository;
import org.idp.server.oauth.repository.ClientConfigurationRepository;
import org.idp.server.oauth.repository.ServerConfigurationRepository;
import org.idp.server.oauth.request.OAuthRequestContextService;
import org.idp.server.oauth.service.NormalPatternContextService;
import org.idp.server.oauth.service.RequestObjectPatternContextService;
import org.idp.server.oauth.service.RequestUriPatternContextService;
import org.idp.server.oauth.validator.OAuthRequestValidator;
import org.idp.server.oauth.verifier.OAuthRequestVerifier;
import org.idp.server.type.OAuthRequestParameters;
import org.idp.server.type.oauth.TokenIssuer;

/** OAuthRequestHandler */
public class OAuthRequestHandler {
  Map<OAuthRequestPattern, OAuthRequestContextService> map = new HashMap<>();
  OAuthRequestValidator requestValidator;
  OAuthRequestAnalyzer requestAnalyzer;
  OAuthRequestVerifier verifier;
  AuthorizationRequestRepository authorizationRequestRepository;
  ServerConfigurationRepository serverConfigurationRepository;
  ClientConfigurationRepository clientConfigurationRepository;

  public OAuthRequestHandler(
      AuthorizationRequestRepository authorizationRequestRepository,
      ServerConfigurationRepository serverConfigurationRepository,
      ClientConfigurationRepository clientConfigurationRepository,
      RequestObjectGateway requestObjectGateway) {
    this.requestValidator = new OAuthRequestValidator();
    this.requestAnalyzer = new OAuthRequestAnalyzer();
    this.verifier = new OAuthRequestVerifier();
    this.authorizationRequestRepository = authorizationRequestRepository;
    this.serverConfigurationRepository = serverConfigurationRepository;
    this.clientConfigurationRepository = clientConfigurationRepository;
    map.put(OAuthRequestPattern.NORMAL, new NormalPatternContextService());
    map.put(OAuthRequestPattern.REQUEST_OBJECT, new RequestObjectPatternContextService());
    map.put(
        OAuthRequestPattern.REQUEST_URI, new RequestUriPatternContextService(requestObjectGateway));
  }

  public OAuthRequestContext handle(OAuthRequest oAuthRequest) {
    OAuthRequestParameters parameters = oAuthRequest.toParameters();
    TokenIssuer tokenIssuer = oAuthRequest.toTokenIssuer();
    requestValidator.validate(parameters);

    ServerConfiguration serverConfiguration = serverConfigurationRepository.get(tokenIssuer);
    ClientConfiguration clientConfiguration =
        clientConfigurationRepository.get(tokenIssuer, parameters.clientId());

    OAuthRequestPattern oAuthRequestPattern = requestAnalyzer.analyzePattern(parameters);
    OAuthRequestContextService oAuthRequestContextService = map.get(oAuthRequestPattern);
    if (Objects.isNull(oAuthRequestContextService)) {
      throw new RuntimeException("not support request pattern");
    }
    OAuthRequestContext context =
        oAuthRequestContextService.create(parameters, serverConfiguration, clientConfiguration);
    verifier.verify(context);
    authorizationRequestRepository.register(context.authorizationRequest());

    return context;
  }
}
