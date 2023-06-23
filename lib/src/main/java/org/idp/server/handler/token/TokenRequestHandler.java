package org.idp.server.handler.token;

import org.idp.server.ciba.repository.BackchannelAuthenticationRequestRepository;
import org.idp.server.ciba.repository.CibaGrantRepository;
import org.idp.server.clientauthenticator.ClientAuthenticatorHandler;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ClientConfigurationRepository;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.configuration.ServerConfigurationRepository;
import org.idp.server.grantmangment.AuthorizationGrantedRepository;
import org.idp.server.handler.token.io.TokenRequest;
import org.idp.server.handler.token.io.TokenRequestResponse;
import org.idp.server.handler.token.io.TokenRequestStatus;
import org.idp.server.oauth.clientcredentials.ClientCredentials;
import org.idp.server.oauth.repository.AuthorizationCodeGrantRepository;
import org.idp.server.oauth.repository.AuthorizationRequestRepository;
import org.idp.server.token.OAuthToken;
import org.idp.server.token.PasswordCredentialsGrantDelegate;
import org.idp.server.token.TokenRequestContext;
import org.idp.server.token.TokenRequestParameters;
import org.idp.server.token.repository.OAuthTokenRepository;
import org.idp.server.token.service.*;
import org.idp.server.token.validator.TokenRequestValidator;
import org.idp.server.type.extension.CustomProperties;
import org.idp.server.type.mtls.ClientCert;
import org.idp.server.type.oauth.ClientId;
import org.idp.server.type.oauth.ClientSecretBasic;
import org.idp.server.type.oauth.TokenIssuer;

public class TokenRequestHandler {

  OAuthTokenCreationServices oAuthTokenCreationServices;
  ClientAuthenticatorHandler clientAuthenticatorHandler;
  OAuthTokenRepository oAuthTokenRepository;
  ServerConfigurationRepository serverConfigurationRepository;
  ClientConfigurationRepository clientConfigurationRepository;

  public TokenRequestHandler(
      AuthorizationRequestRepository authorizationRequestRepository,
      AuthorizationCodeGrantRepository authorizationCodeGrantRepository,
      AuthorizationGrantedRepository authorizationGrantedRepository,
      BackchannelAuthenticationRequestRepository backchannelAuthenticationRequestRepository,
      CibaGrantRepository cibaGrantRepository,
      OAuthTokenRepository oAuthTokenRepository,
      ServerConfigurationRepository serverConfigurationRepository,
      ClientConfigurationRepository clientConfigurationRepository) {
    this.oAuthTokenCreationServices =
        new OAuthTokenCreationServices(
            authorizationRequestRepository,
            authorizationCodeGrantRepository,
            authorizationGrantedRepository,
            backchannelAuthenticationRequestRepository,
            cibaGrantRepository,
            oAuthTokenRepository);
    this.clientAuthenticatorHandler = new ClientAuthenticatorHandler();
    this.oAuthTokenRepository = oAuthTokenRepository;
    this.serverConfigurationRepository = serverConfigurationRepository;
    this.clientConfigurationRepository = clientConfigurationRepository;
  }

  public TokenRequestResponse handle(
      TokenRequest tokenRequest,
      PasswordCredentialsGrantDelegate passwordCredentialsGrantDelegate) {
    TokenIssuer tokenIssuer = tokenRequest.toTokenIssuer();
    TokenRequestParameters parameters = tokenRequest.toParameters();
    TokenRequestValidator baseValidator = new TokenRequestValidator(parameters);
    baseValidator.validate();

    ClientSecretBasic clientSecretBasic = tokenRequest.clientSecretBasic();
    ClientCert clientCert = tokenRequest.toClientCert();
    ClientId clientId = tokenRequest.clientId();
    CustomProperties customProperties = tokenRequest.toCustomProperties();
    ServerConfiguration serverConfiguration = serverConfigurationRepository.get(tokenIssuer);
    ClientConfiguration clientConfiguration =
        clientConfigurationRepository.get(tokenIssuer, clientId);

    TokenRequestContext tokenRequestContext =
        new TokenRequestContext(
            clientSecretBasic,
            clientCert,
            parameters,
            customProperties,
            passwordCredentialsGrantDelegate,
            serverConfiguration,
            clientConfiguration);

    ClientCredentials clientCredentials =
        clientAuthenticatorHandler.authenticate(tokenRequestContext);

    OAuthTokenCreationService oAuthTokenCreationService =
        oAuthTokenCreationServices.get(tokenRequestContext.grantType());

    OAuthToken oAuthToken =
        oAuthTokenCreationService.create(tokenRequestContext, clientCredentials);

    return new TokenRequestResponse(TokenRequestStatus.OK, oAuthToken);
  }
}
