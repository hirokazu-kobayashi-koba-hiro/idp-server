package org.idp.server.handler;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.identity.User;
import org.idp.server.core.oauth.OAuthAuthorizeContext;
import org.idp.server.core.oauth.grant.AuthorizationCodeGrant;
import org.idp.server.core.oauth.grant.AuthorizationCodeGrantCreator;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.oauth.response.AuthorizationCodeResponseCreator;
import org.idp.server.core.oauth.response.AuthorizationResponse;
import org.idp.server.core.oauth.response.AuthorizationResponseCreator;
import org.idp.server.core.repository.AuthorizationCodeGrantRepository;
import org.idp.server.core.repository.AuthorizationRequestRepository;
import org.idp.server.core.repository.ClientConfigurationRepository;
import org.idp.server.core.repository.ServerConfigurationRepository;
import org.idp.server.core.type.extension.CustomProperties;
import org.idp.server.core.type.oauth.ClientId;
import org.idp.server.core.type.oauth.ResponseType;
import org.idp.server.core.type.oauth.TokenIssuer;

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

  public AuthorizationResponse handle(
      TokenIssuer tokenIssuer,
      AuthorizationRequestIdentifier authorizationRequestIdentifier,
      User user,
      CustomProperties customProperties) {
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
