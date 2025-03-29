package org.idp.server.core.handler.oauth;

import java.util.Objects;
import org.idp.server.core.basic.date.SystemDateTime;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ClientConfigurationRepository;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.configuration.ServerConfigurationRepository;
import org.idp.server.core.handler.oauth.io.OAuthAuthorizeRequest;
import org.idp.server.core.oauth.OAuthAuthorizeContext;
import org.idp.server.core.oauth.OAuthRequestDelegate;
import org.idp.server.core.oauth.OAuthSession;
import org.idp.server.core.oauth.OAuthSessionKey;
import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.grant.AuthorizationCodeGrant;
import org.idp.server.core.oauth.grant.AuthorizationCodeGrantCreator;
import org.idp.server.core.oauth.grant.AuthorizationGrant;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.repository.*;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.oauth.response.*;
import org.idp.server.core.oauth.validator.OAuthAuthorizeRequestValidator;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.token.*;
import org.idp.server.core.token.repository.OAuthTokenRepository;
import org.idp.server.core.type.extension.CustomProperties;
import org.idp.server.core.type.oauth.RequestedClientId;

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

  public AuthorizationResponse handle(
      OAuthAuthorizeRequest request, OAuthRequestDelegate delegate) {

    Tenant tenant = request.tenant();
    AuthorizationRequestIdentifier authorizationRequestIdentifier = request.toIdentifier();
    User user = request.user();
    Authentication authentication = request.authentication();
    CustomProperties customProperties = request.toCustomProperties();

    OAuthAuthorizeRequestValidator validator =
        new OAuthAuthorizeRequestValidator(
            authorizationRequestIdentifier, user, authentication, customProperties);
    validator.validate();

    AuthorizationRequest authorizationRequest =
        authorizationRequestRepository.get(authorizationRequestIdentifier);
    RequestedClientId requestedClientId = authorizationRequest.clientId();
    ServerConfiguration serverConfiguration =
        serverConfigurationRepository.get(tenant.identifier());
    ClientConfiguration clientConfiguration =
        clientConfigurationRepository.get(tenant, requestedClientId);

    OAuthAuthorizeContext context =
        new OAuthAuthorizeContext(
            authorizationRequest,
            user,
            authentication,
            customProperties,
            serverConfiguration,
            clientConfiguration);

    AuthorizationResponseCreator authorizationResponseCreator =
        creators.get(context.responseType());
    AuthorizationResponse authorizationResponse = authorizationResponseCreator.create(context);

    AuthorizationGrant authorizationGrant = context.authorize();
    if (authorizationResponse.hasAuthorizationCode()) {
      AuthorizationCodeGrant authorizationCodeGrant =
          AuthorizationCodeGrantCreator.create(context, authorizationResponse);
      authorizationCodeGrantRepository.register(authorizationCodeGrant);
    }

    if (authorizationResponse.hasAccessToken()) {
      OAuthToken oAuthToken = OAuthTokenFactory.create(authorizationResponse, authorizationGrant);
      oAuthTokenRepository.register(oAuthToken);
    }

    if (Objects.nonNull(delegate)) {
      OAuthSessionKey oAuthSessionKey =
          new OAuthSessionKey(tenant.identifierValue(), requestedClientId.value());
      OAuthSession session =
          new OAuthSession(
              oAuthSessionKey, user, authentication, SystemDateTime.now().plusSeconds(3600));
      delegate.registerSession(session);
    }

    return authorizationResponse;
  }
}
