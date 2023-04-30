package org.idp.server.handler.token;

import static org.idp.server.type.oauth.GrantType.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.clientauthenticator.ClientAuthenticatorHandler;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.grantmangment.AuthorizationGrantedRepository;
import org.idp.server.handler.token.io.TokenRequest;
import org.idp.server.oauth.repository.AuthorizationCodeGrantRepository;
import org.idp.server.oauth.repository.AuthorizationRequestRepository;
import org.idp.server.oauth.repository.ClientConfigurationRepository;
import org.idp.server.oauth.repository.ServerConfigurationRepository;
import org.idp.server.token.OAuthToken;
import org.idp.server.token.TokenRequestContext;
import org.idp.server.token.TokenRequestParameters;
import org.idp.server.token.repository.OAuthTokenRepository;
import org.idp.server.token.service.ClientCredentialsGrantService;
import org.idp.server.token.service.OAuthTokenCreationService;
import org.idp.server.token.service.RefreshTokenGrantService;
import org.idp.server.token.service.TokenCreationCodeGrantService;
import org.idp.server.token.validator.TokenRequestValidator;
import org.idp.server.type.extension.CustomProperties;
import org.idp.server.type.oauth.ClientId;
import org.idp.server.type.oauth.ClientSecretBasic;
import org.idp.server.type.oauth.GrantType;
import org.idp.server.type.oauth.TokenIssuer;

public class TokenRequestHandler {

  Map<GrantType, OAuthTokenCreationService> map = new HashMap<>();
  TokenRequestValidator tokenRequestValidator;
  ClientAuthenticatorHandler clientAuthenticatorHandler;
  OAuthTokenRepository oAuthTokenRepository;
  ServerConfigurationRepository serverConfigurationRepository;
  ClientConfigurationRepository clientConfigurationRepository;

  public TokenRequestHandler(
      AuthorizationRequestRepository authorizationRequestRepository,
      AuthorizationCodeGrantRepository authorizationCodeGrantRepository,
      AuthorizationGrantedRepository authorizationGrantedRepository,
      OAuthTokenRepository oAuthTokenRepository,
      ServerConfigurationRepository serverConfigurationRepository,
      ClientConfigurationRepository clientConfigurationRepository) {
    map.put(
        authorization_code,
        new TokenCreationCodeGrantService(
            authorizationRequestRepository,
            oAuthTokenRepository,
            authorizationCodeGrantRepository,
            authorizationGrantedRepository));
    map.put(refresh_token, new RefreshTokenGrantService(oAuthTokenRepository));
    map.put(client_credentials, new ClientCredentialsGrantService(oAuthTokenRepository));
    this.tokenRequestValidator = new TokenRequestValidator();
    this.clientAuthenticatorHandler = new ClientAuthenticatorHandler();
    this.oAuthTokenRepository = oAuthTokenRepository;
    this.serverConfigurationRepository = serverConfigurationRepository;
    this.clientConfigurationRepository = clientConfigurationRepository;
  }

  public OAuthToken handle(TokenRequest tokenRequest) {
    TokenIssuer tokenIssuer = tokenRequest.toTokenIssuer();
    TokenRequestParameters parameters = tokenRequest.toParameters();
    ClientSecretBasic clientSecretBasic = tokenRequest.clientSecretBasic();
    ClientId clientId = tokenRequest.clientId();
    CustomProperties customProperties = tokenRequest.toCustomProperties();
    ServerConfiguration serverConfiguration = serverConfigurationRepository.get(tokenIssuer);
    ClientConfiguration clientConfiguration =
        clientConfigurationRepository.get(tokenIssuer, clientId);
    // TODO request validate
    TokenRequestContext tokenRequestContext =
        new TokenRequestContext(
            clientSecretBasic,
            parameters,
            customProperties,
            serverConfiguration,
            clientConfiguration);
    tokenRequestValidator.validate(tokenRequestContext);

    clientAuthenticatorHandler.authenticate(tokenRequestContext);

    GrantType grantType = tokenRequestContext.grantType();
    OAuthTokenCreationService oAuthTokenCreationService = map.get(grantType);
    if (Objects.isNull(oAuthTokenCreationService)) {
      throw new RuntimeException(String.format("unsupported grant_type (%s)", grantType.name()));
    }

    return oAuthTokenCreationService.create(tokenRequestContext);
  }
}
