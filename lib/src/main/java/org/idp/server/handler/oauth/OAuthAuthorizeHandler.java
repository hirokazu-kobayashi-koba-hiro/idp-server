package org.idp.server.handler.oauth;

import static org.idp.server.type.oauth.ResponseType.*;

import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.handler.oauth.io.OAuthAuthorizeRequest;
import org.idp.server.handler.oauth.io.OAuthAuthorizeResponse;
import org.idp.server.handler.oauth.io.OAuthAuthorizeStatus;
import org.idp.server.oauth.OAuthAuthorizeContext;
import org.idp.server.oauth.grant.AuthorizationCodeGrant;
import org.idp.server.oauth.grant.AuthorizationCodeGrantCreator;
import org.idp.server.oauth.grant.AuthorizationGrant;
import org.idp.server.oauth.identity.User;
import org.idp.server.oauth.repository.*;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.oauth.response.*;
import org.idp.server.token.*;
import org.idp.server.token.repository.OAuthTokenRepository;
import org.idp.server.type.extension.CustomProperties;
import org.idp.server.type.oauth.ClientId;
import org.idp.server.type.oauth.TokenIssuer;

/** OAuthAuthorizeHandler */
public class OAuthAuthorizeHandler {

  AuthorizationResponseCreators creators;
  AuthorizationRequestRepository authorizationRequestRepository;
  AuthorizationCodeGrantRepository authorizationCodeGrantRepository;
  OAuthTokenRepository oAuthTokenRepository;
  ServerConfigurationRepository serverConfigurationRepository;
  ClientConfigurationRepository clientConfigurationRepository;

  public OAuthAuthorizeHandler(
      AuthorizationRequestRepository authorizationRequestRepository,
      AuthorizationCodeGrantRepository authorizationCodeGrantRepository,
      OAuthTokenRepository oAuthTokenRepository,
      ServerConfigurationRepository serverConfigurationRepository,
      ClientConfigurationRepository clientConfigurationRepository) {
    this.authorizationRequestRepository = authorizationRequestRepository;
    this.authorizationCodeGrantRepository = authorizationCodeGrantRepository;
    this.oAuthTokenRepository = oAuthTokenRepository;
    this.serverConfigurationRepository = serverConfigurationRepository;
    this.clientConfigurationRepository = clientConfigurationRepository;
    this.creators = new AuthorizationResponseCreators();
  }

  public OAuthAuthorizeResponse handle(OAuthAuthorizeRequest request) {
    TokenIssuer tokenIssuer = request.toTokenIssuer();
    AuthorizationRequestIdentifier authorizationRequestIdentifier = request.toIdentifier();
    User user = request.user();
    CustomProperties customProperties = request.toCustomProperties();

    AuthorizationRequest authorizationRequest =
        authorizationRequestRepository.get(authorizationRequestIdentifier);
    ClientId clientId = authorizationRequest.clientId();
    ServerConfiguration serverConfiguration = serverConfigurationRepository.get(tokenIssuer);
    ClientConfiguration clientConfiguration =
        clientConfigurationRepository.get(tokenIssuer, clientId);
    OAuthAuthorizeContext context =
        new OAuthAuthorizeContext(
            authorizationRequest, user, customProperties, serverConfiguration, clientConfiguration);

    AuthorizationResponseCreator authorizationResponseCreator =
        creators.get(context.responseType());

    AuthorizationResponse authorizationResponse = authorizationResponseCreator.create(context);
    AuthorizationGrant authorizationGrant = context.toAuthorizationGranted();
    if (authorizationResponse.hasAuthorizationCode()) {
      AuthorizationCodeGrant authorizationCodeGrant =
          AuthorizationCodeGrantCreator.create(context, authorizationResponse);
      authorizationCodeGrantRepository.register(authorizationCodeGrant);
    }

    if (authorizationResponse.hasAccessToken()) {
      OAuthToken oAuthToken = OAuthTokenFactory.create(authorizationResponse, authorizationGrant);
      oAuthTokenRepository.register(oAuthToken);
    }

    return new OAuthAuthorizeResponse(OAuthAuthorizeStatus.OK, authorizationResponse);
  }
}
