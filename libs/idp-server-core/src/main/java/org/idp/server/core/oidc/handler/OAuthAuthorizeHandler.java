package org.idp.server.core.oidc.handler;

import org.idp.server.basic.type.extension.CustomProperties;
import org.idp.server.basic.type.oauth.RequestedClientId;
import org.idp.server.core.identity.User;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.OAuthAuthorizeContext;
import org.idp.server.core.oidc.OAuthSession;
import org.idp.server.core.oidc.OAuthSessionDelegate;
import org.idp.server.core.oidc.OAuthSessionKey;
import org.idp.server.core.oidc.authentication.Authentication;
import org.idp.server.core.oidc.configuration.ClientConfiguration;
import org.idp.server.core.oidc.configuration.ClientConfigurationRepository;
import org.idp.server.core.oidc.configuration.ServerConfiguration;
import org.idp.server.core.oidc.configuration.ServerConfigurationRepository;
import org.idp.server.core.oidc.grant.AuthorizationCodeGrant;
import org.idp.server.core.oidc.grant.AuthorizationCodeGrantCreator;
import org.idp.server.core.oidc.grant.AuthorizationGrant;
import org.idp.server.core.oidc.io.OAuthAuthorizeRequest;
import org.idp.server.core.oidc.repository.*;
import org.idp.server.core.oidc.request.AuthorizationRequest;
import org.idp.server.core.oidc.request.AuthorizationRequestIdentifier;
import org.idp.server.core.oidc.response.*;
import org.idp.server.core.oidc.validator.OAuthAuthorizeRequestValidator;
import org.idp.server.core.token.*;
import org.idp.server.core.token.repository.OAuthTokenRepository;

/** OAuthAuthorizeHandler */
public class OAuthAuthorizeHandler {

  AuthorizationResponseCreators creators;
  AuthorizationRequestRepository authorizationRequestRepository;
  AuthorizationCodeGrantRepository authorizationCodeGrantRepository;
  OAuthTokenRepository oAuthTokenRepository;
  ServerConfigurationRepository serverConfigurationRepository;
  ClientConfigurationRepository clientConfigurationRepository;

  public OAuthAuthorizeHandler(AuthorizationRequestRepository authorizationRequestRepository, AuthorizationCodeGrantRepository authorizationCodeGrantRepository, OAuthTokenRepository oAuthTokenRepository, ServerConfigurationRepository serverConfigurationRepository, ClientConfigurationRepository clientConfigurationRepository) {
    this.authorizationRequestRepository = authorizationRequestRepository;
    this.authorizationCodeGrantRepository = authorizationCodeGrantRepository;
    this.oAuthTokenRepository = oAuthTokenRepository;
    this.serverConfigurationRepository = serverConfigurationRepository;
    this.clientConfigurationRepository = clientConfigurationRepository;
    this.creators = new AuthorizationResponseCreators();
  }

  public AuthorizationResponse handle(OAuthAuthorizeRequest request, OAuthSessionDelegate delegate) {

    Tenant tenant = request.tenant();
    AuthorizationRequestIdentifier authorizationRequestIdentifier = request.toIdentifier();
    User user = request.user();
    Authentication authentication = request.authentication();
    CustomProperties customProperties = request.toCustomProperties();

    OAuthAuthorizeRequestValidator validator = new OAuthAuthorizeRequestValidator(authorizationRequestIdentifier, user, authentication, customProperties);
    validator.validate();

    AuthorizationRequest authorizationRequest = authorizationRequestRepository.get(tenant, authorizationRequestIdentifier);
    RequestedClientId requestedClientId = authorizationRequest.retrieveClientId();
    ServerConfiguration serverConfiguration = serverConfigurationRepository.get(tenant);
    ClientConfiguration clientConfiguration = clientConfigurationRepository.get(tenant, requestedClientId);

    OAuthAuthorizeContext context = new OAuthAuthorizeContext(authorizationRequest, user, authentication, customProperties, serverConfiguration, clientConfiguration);

    AuthorizationResponseCreator authorizationResponseCreator = creators.get(context.responseType());
    AuthorizationResponse authorizationResponse = authorizationResponseCreator.create(context);

    AuthorizationGrant authorizationGrant = context.authorize();
    if (authorizationResponse.hasAuthorizationCode()) {
      AuthorizationCodeGrant authorizationCodeGrant = AuthorizationCodeGrantCreator.create(context, authorizationResponse);
      authorizationCodeGrantRepository.register(tenant, authorizationCodeGrant);
    }

    if (authorizationResponse.hasAccessToken()) {
      OAuthToken oAuthToken = OAuthTokenFactory.create(authorizationResponse, authorizationGrant);
      oAuthTokenRepository.register(tenant, oAuthToken);
    }

    OAuthSessionKey oAuthSessionKey = new OAuthSessionKey(tenant.identifierValue(), requestedClientId.value());
    OAuthSession session = OAuthSession.create(oAuthSessionKey, user, authentication, authorizationRequest.maxAge());
    delegate.registerSession(session);

    return authorizationResponse;
  }
}
