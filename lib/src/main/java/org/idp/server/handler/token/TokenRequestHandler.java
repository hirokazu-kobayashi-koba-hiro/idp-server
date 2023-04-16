package org.idp.server.handler.token;

import static org.idp.server.type.oauth.GrantType.authorization_code;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.handler.io.TokenRequest;
import org.idp.server.oauth.repository.AuthorizationCodeGrantRepository;
import org.idp.server.oauth.repository.AuthorizationRequestRepository;
import org.idp.server.oauth.repository.ClientConfigurationRepository;
import org.idp.server.oauth.repository.ServerConfigurationRepository;
import org.idp.server.token.OAuthToken;
import org.idp.server.token.OAuthTokenCreationService;
import org.idp.server.token.TokenRequestContext;
import org.idp.server.token.TokenRequestParameters;
import org.idp.server.token.repository.OAuthTokenRepository;
import org.idp.server.token.service.TokenCreationCodeGrantService;
import org.idp.server.token.validator.TokenRequestValidator;
import org.idp.server.type.oauth.ClientId;
import org.idp.server.type.oauth.ClientSecretBasic;
import org.idp.server.type.oauth.GrantType;
import org.idp.server.type.oauth.TokenIssuer;

public class TokenRequestHandler {

  Map<GrantType, OAuthTokenCreationService> map = new HashMap<>();
  TokenRequestValidator tokenRequestValidator;
  OAuthTokenRepository oAuthTokenRepository;
  ServerConfigurationRepository serverConfigurationRepository;
  ClientConfigurationRepository clientConfigurationRepository;

  public TokenRequestHandler(
      AuthorizationRequestRepository authorizationRequestRepository,
      AuthorizationCodeGrantRepository authorizationCodeGrantRepository,
      OAuthTokenRepository oAuthTokenRepository,
      ServerConfigurationRepository serverConfigurationRepository,
      ClientConfigurationRepository clientConfigurationRepository) {
    map.put(
        authorization_code,
        new TokenCreationCodeGrantService(
            authorizationRequestRepository, authorizationCodeGrantRepository));
    this.tokenRequestValidator = new TokenRequestValidator();
    this.oAuthTokenRepository = oAuthTokenRepository;
    this.serverConfigurationRepository = serverConfigurationRepository;
    this.clientConfigurationRepository = clientConfigurationRepository;
  }

  public OAuthToken handle(TokenRequest tokenRequest) {
    TokenIssuer tokenIssuer = tokenRequest.toTokenIssuer();
    TokenRequestParameters parameters = tokenRequest.toParameters();
    ClientSecretBasic clientSecretBasic = tokenRequest.clientSecretBasic();
    ClientId clientId = tokenRequest.clientId();
    ServerConfiguration serverConfiguration = serverConfigurationRepository.get(tokenIssuer);
    ClientConfiguration clientConfiguration =
        clientConfigurationRepository.get(tokenIssuer, clientId);
    // TODO request validate
    TokenRequestContext tokenRequestContext =
        new TokenRequestContext(
            clientSecretBasic, parameters, serverConfiguration, clientConfiguration);
    tokenRequestValidator.validate(tokenRequestContext);

    GrantType grantType = tokenRequestContext.grantType();
    OAuthTokenCreationService oAuthTokenCreationService = map.get(grantType);
    if (Objects.isNull(oAuthTokenCreationService)) {
      throw new RuntimeException(String.format("unsupported grant_type (%s)", grantType.name()));
    }

    OAuthToken oAuthToken = oAuthTokenCreationService.create(tokenRequestContext);
    oAuthTokenRepository.register(oAuthToken);

    return oAuthToken;
  }
}
