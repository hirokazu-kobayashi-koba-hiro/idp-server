package org.idp.server.core.token.handler.token;

import java.util.Map;
import org.idp.server.basic.type.extension.CustomProperties;
import org.idp.server.basic.type.mtls.ClientCert;
import org.idp.server.basic.type.oauth.ClientSecretBasic;
import org.idp.server.basic.type.oauth.GrantType;
import org.idp.server.basic.type.oauth.RequestedClientId;
import org.idp.server.core.grant_management.AuthorizationGrantedRepository;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.clientauthenticator.ClientAuthenticatorHandler;
import org.idp.server.core.oidc.clientcredentials.ClientCredentials;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.oidc.repository.AuthorizationCodeGrantRepository;
import org.idp.server.core.oidc.repository.AuthorizationRequestRepository;
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

public class TokenRequestHandler {

  OAuthTokenCreationServices oAuthTokenCreationServices;
  ClientAuthenticatorHandler clientAuthenticatorHandler;
  OAuthTokenRepository oAuthTokenRepository;
  AuthorizationServerConfigurationQueryRepository authorizationServerConfigurationQueryRepository;
  ClientConfigurationQueryRepository clientConfigurationQueryRepository;

  public TokenRequestHandler(
      AuthorizationRequestRepository authorizationRequestRepository,
      AuthorizationCodeGrantRepository authorizationCodeGrantRepository,
      AuthorizationGrantedRepository authorizationGrantedRepository,
      OAuthTokenRepository oAuthTokenRepository,
      AuthorizationServerConfigurationQueryRepository
          authorizationServerConfigurationQueryRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository,
      Map<GrantType, OAuthTokenCreationService> extensionOAuthTokenCreationServices) {
    this.oAuthTokenCreationServices =
        new OAuthTokenCreationServices(
            authorizationRequestRepository,
            authorizationCodeGrantRepository,
            authorizationGrantedRepository,
            oAuthTokenRepository,
            extensionOAuthTokenCreationServices);
    this.clientAuthenticatorHandler = new ClientAuthenticatorHandler();
    this.oAuthTokenRepository = oAuthTokenRepository;
    this.authorizationServerConfigurationQueryRepository =
        authorizationServerConfigurationQueryRepository;
    this.clientConfigurationQueryRepository = clientConfigurationQueryRepository;
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
    AuthorizationServerConfiguration authorizationServerConfiguration =
        authorizationServerConfigurationQueryRepository.get(tenant);
    ClientConfiguration clientConfiguration =
        clientConfigurationQueryRepository.get(tenant, requestedClientId);

    TokenRequestContext tokenRequestContext =
        new TokenRequestContext(
            tenant,
            clientSecretBasic,
            clientCert,
            parameters,
            customProperties,
            passwordCredentialsGrantDelegate,
            authorizationServerConfiguration,
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
