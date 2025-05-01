package org.idp.server.core.token.handler.token;

import org.idp.server.core.ciba.repository.BackchannelAuthenticationRequestRepository;
import org.idp.server.core.ciba.repository.CibaGrantRepository;
import org.idp.server.core.oidc.clientauthenticator.ClientAuthenticatorHandler;
import org.idp.server.core.oidc.configuration.ClientConfiguration;
import org.idp.server.core.oidc.configuration.ClientConfigurationRepository;
import org.idp.server.core.oidc.configuration.ServerConfiguration;
import org.idp.server.core.oidc.configuration.ServerConfigurationRepository;
import org.idp.server.core.grantmangment.AuthorizationGrantedRepository;
import org.idp.server.core.oidc.clientcredentials.ClientCredentials;
import org.idp.server.core.oidc.repository.AuthorizationCodeGrantRepository;
import org.idp.server.core.oidc.repository.AuthorizationRequestRepository;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.token.OAuthToken;
import org.idp.server.core.token.PasswordCredentialsGrantDelegate;
import org.idp.server.core.token.TokenRequestContext;
import org.idp.server.core.token.TokenRequestParameters;
import org.idp.server.core.token.handler.token.io.TokenRequest;
import org.idp.server.core.token.handler.token.io.TokenRequestResponse;
import org.idp.server.core.token.handler.token.io.TokenRequestStatus;
import org.idp.server.core.token.repository.OAuthTokenRepository;
import org.idp.server.core.token.service.*;
import org.idp.server.core.token.validator.TokenRequestValidator;
import org.idp.server.basic.type.extension.CustomProperties;
import org.idp.server.basic.type.mtls.ClientCert;
import org.idp.server.basic.type.oauth.ClientSecretBasic;
import org.idp.server.basic.type.oauth.RequestedClientId;

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
    Tenant tenant = tokenRequest.tenant();
    TokenRequestParameters parameters = tokenRequest.toParameters();
    TokenRequestValidator baseValidator = new TokenRequestValidator(parameters);
    baseValidator.validate();

    ClientSecretBasic clientSecretBasic = tokenRequest.clientSecretBasic();
    ClientCert clientCert = tokenRequest.toClientCert();
    RequestedClientId requestedClientId = tokenRequest.clientId();
    CustomProperties customProperties = tokenRequest.toCustomProperties();
    ServerConfiguration serverConfiguration = serverConfigurationRepository.get(tenant);
    ClientConfiguration clientConfiguration =
        clientConfigurationRepository.get(tenant, requestedClientId);

    TokenRequestContext tokenRequestContext =
        new TokenRequestContext(
            tenant,
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
