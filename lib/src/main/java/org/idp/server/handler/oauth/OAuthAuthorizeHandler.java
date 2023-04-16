package org.idp.server.handler.oauth;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.handler.io.OAuthAuthorizeRequest;
import org.idp.server.oauth.OAuthAuthorizeContext;
import org.idp.server.oauth.grant.AuthorizationCodeGrant;
import org.idp.server.oauth.grant.AuthorizationCodeGrantCreator;
import org.idp.server.oauth.identity.User;
import org.idp.server.oauth.repository.AuthorizationCodeGrantRepository;
import org.idp.server.oauth.repository.AuthorizationRequestRepository;
import org.idp.server.oauth.repository.ClientConfigurationRepository;
import org.idp.server.oauth.repository.ServerConfigurationRepository;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.oauth.response.AuthorizationCodeResponseCreator;
import org.idp.server.oauth.response.AuthorizationResponse;
import org.idp.server.oauth.response.AuthorizationResponseCreator;
import org.idp.server.type.extension.CustomProperties;
import org.idp.server.type.oauth.ClientId;
import org.idp.server.type.oauth.ResponseType;
import org.idp.server.type.oauth.TokenIssuer;

/** OAuthAuthorizeHandler */
public class OAuthAuthorizeHandler {

  Map<ResponseType, AuthorizationResponseCreator> map = new HashMap<>();
  AuthorizationRequestRepository authorizationRequestRepository;
  AuthorizationCodeGrantRepository authorizationCodeGrantRepository;
  ServerConfigurationRepository serverConfigurationRepository;
  ClientConfigurationRepository clientConfigurationRepository;

  public OAuthAuthorizeHandler(
      AuthorizationRequestRepository authorizationRequestRepository,
      AuthorizationCodeGrantRepository authorizationCodeGrantRepository,
      ServerConfigurationRepository serverConfigurationRepository,
      ClientConfigurationRepository clientConfigurationRepository) {
    this.authorizationRequestRepository = authorizationRequestRepository;
    this.authorizationCodeGrantRepository = authorizationCodeGrantRepository;
    this.serverConfigurationRepository = serverConfigurationRepository;
    this.clientConfigurationRepository = clientConfigurationRepository;
    map.put(ResponseType.code, new AuthorizationCodeResponseCreator());
  }

  public AuthorizationResponse handle(OAuthAuthorizeRequest request) {
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

    AuthorizationResponseCreator authorizationResponseCreator = map.get(context.responseType());
    if (Objects.isNull(authorizationResponseCreator)) {
      throw new RuntimeException("not support request type");
    }

    AuthorizationResponse authorizationResponse = authorizationResponseCreator.create(context);

    if (authorizationResponse.hasAuthorizationCode()) {
      AuthorizationCodeGrant authorizationCodeGrant =
          AuthorizationCodeGrantCreator.create(context, authorizationResponse);
      authorizationCodeGrantRepository.register(authorizationCodeGrant);
    }

    return authorizationResponse;
  }
}
