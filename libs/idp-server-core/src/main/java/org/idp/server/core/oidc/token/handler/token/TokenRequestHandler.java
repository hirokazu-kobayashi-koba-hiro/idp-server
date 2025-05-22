/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.token.handler.token;

import java.util.Map;
import org.idp.server.basic.type.extension.CustomProperties;
import org.idp.server.basic.type.mtls.ClientCert;
import org.idp.server.basic.type.oauth.ClientSecretBasic;
import org.idp.server.basic.type.oauth.GrantType;
import org.idp.server.basic.type.oauth.RequestedClientId;
import org.idp.server.core.oidc.clientauthenticator.ClientAuthenticationHandler;
import org.idp.server.core.oidc.clientcredentials.ClientCredentials;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.oidc.grant_management.AuthorizationGrantedRepository;
import org.idp.server.core.oidc.repository.AuthorizationCodeGrantRepository;
import org.idp.server.core.oidc.repository.AuthorizationRequestRepository;
import org.idp.server.core.oidc.token.OAuthToken;
import org.idp.server.core.oidc.token.PasswordCredentialsGrantDelegate;
import org.idp.server.core.oidc.token.TokenRequestContext;
import org.idp.server.core.oidc.token.TokenRequestParameters;
import org.idp.server.core.oidc.token.handler.token.io.TokenRequest;
import org.idp.server.core.oidc.token.handler.token.io.TokenRequestResponse;
import org.idp.server.core.oidc.token.handler.token.io.TokenRequestStatus;
import org.idp.server.core.oidc.token.repository.OAuthTokenRepository;
import org.idp.server.core.oidc.token.service.*;
import org.idp.server.core.oidc.token.validator.TokenRequestValidator;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class TokenRequestHandler {

  OAuthTokenCreationServices oAuthTokenCreationServices;
  ClientAuthenticationHandler clientAuthenticationHandler;
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
    this.clientAuthenticationHandler = new ClientAuthenticationHandler();
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
        clientAuthenticationHandler.authenticate(tokenRequestContext);

    OAuthTokenCreationService oAuthTokenCreationService =
        oAuthTokenCreationServices.get(tokenRequestContext.grantType());

    OAuthToken oAuthToken =
        oAuthTokenCreationService.create(tokenRequestContext, clientCredentials);

    return new TokenRequestResponse(TokenRequestStatus.OK, oAuthToken);
  }
}
