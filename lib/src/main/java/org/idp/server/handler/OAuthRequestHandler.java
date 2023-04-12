package org.idp.server.handler;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.httpclient.RequestObjectHttpClient;
import org.idp.server.oauth.OAuthRequestAnalyzer;
import org.idp.server.oauth.OAuthRequestContext;
import org.idp.server.oauth.OAuthRequestPattern;
import org.idp.server.oauth.request.OAuthRequestContextService;
import org.idp.server.repository.AuthorizationRequestRepository;
import org.idp.server.repository.ClientConfigurationRepository;
import org.idp.server.repository.ServerConfigurationRepository;
import org.idp.server.service.NormalPatternContextService;
import org.idp.server.service.RequestObjectPatternContextService;
import org.idp.server.service.RequestUriPatternContextService;
import org.idp.server.type.OAuthRequestParameters;
import org.idp.server.type.oauth.TokenIssuer;
import org.idp.server.verifier.OAuthRequestVerifier;

/** OAuthRequestHandler */
public class OAuthRequestHandler {
  Map<OAuthRequestPattern, OAuthRequestContextService> map = new HashMap<>();
  OAuthRequestAnalyzer requestAnalyzer;
  OAuthRequestVerifier verifier;
  AuthorizationRequestRepository authorizationRequestRepository;
  ServerConfigurationRepository serverConfigurationRepository;
  ClientConfigurationRepository clientConfigurationRepository;

  public OAuthRequestHandler(
      AuthorizationRequestRepository authorizationRequestRepository,
      ServerConfigurationRepository serverConfigurationRepository,
      ClientConfigurationRepository clientConfigurationRepository) {
    this.requestAnalyzer = new OAuthRequestAnalyzer();
    this.verifier = new OAuthRequestVerifier();
    this.authorizationRequestRepository = authorizationRequestRepository;
    this.serverConfigurationRepository = serverConfigurationRepository;
    this.clientConfigurationRepository = clientConfigurationRepository;
    map.put(OAuthRequestPattern.NORMAL, new NormalPatternContextService());
    map.put(OAuthRequestPattern.REQUEST_OBJECT, new RequestObjectPatternContextService());
    map.put(
        OAuthRequestPattern.REQUEST_URI,
        new RequestUriPatternContextService(new RequestObjectHttpClient()));
  }

  public OAuthRequestContext handle(OAuthRequestParameters parameters, TokenIssuer tokenIssuer) {

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
