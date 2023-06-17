package org.idp.server.token.service;

import java.util.UUID;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.oauth.authentication.Authentication;
import org.idp.server.oauth.grant.AuthorizationGrant;
import org.idp.server.oauth.identity.ClaimsPayload;
import org.idp.server.oauth.identity.User;
import org.idp.server.oauth.rar.AuthorizationDetails;
import org.idp.server.oauth.token.AccessToken;
import org.idp.server.oauth.token.AccessTokenCreatable;
import org.idp.server.token.*;
import org.idp.server.token.repository.OAuthTokenRepository;
import org.idp.server.token.validator.ClientCredentialsGrantValidator;
import org.idp.server.token.verifier.ClientCredentialsGrantVerifier;
import org.idp.server.type.extension.CustomProperties;
import org.idp.server.type.oauth.Scopes;

public class ClientCredentialsGrantService
    implements OAuthTokenCreationService, AccessTokenCreatable {
  OAuthTokenRepository oAuthTokenRepository;

  public ClientCredentialsGrantService(OAuthTokenRepository oAuthTokenRepository) {
    this.oAuthTokenRepository = oAuthTokenRepository;
  }

  @Override
  public OAuthToken create(TokenRequestContext context) {
    ClientCredentialsGrantValidator validator = new ClientCredentialsGrantValidator(context);
    validator.validate();

    ServerConfiguration serverConfiguration = context.serverConfiguration();
    ClientConfiguration clientConfiguration = context.clientConfiguration();

    Scopes scopes =
        new Scopes(clientConfiguration.filteredScope(context.scopes().toStringValues()));
    ClientCredentialsGrantVerifier verifier = new ClientCredentialsGrantVerifier(scopes);
    verifier.verify();

    CustomProperties customProperties = context.customProperties();
    // TODO
    AuthorizationGrant authorizationGrant =
        new AuthorizationGrant(
            new User(),
            new Authentication(),
            clientConfiguration.clientId(),
            scopes,
            new ClaimsPayload(),
            customProperties,
            new AuthorizationDetails());

    AccessToken accessToken =
        createAccessToken(authorizationGrant, serverConfiguration, clientConfiguration);

    OAuthToken oAuthToken =
        new OAuthTokenBuilder(new OAuthTokenIdentifier(UUID.randomUUID().toString()))
            .add(accessToken)
            .build();

    oAuthTokenRepository.register(oAuthToken);
    return oAuthToken;
  }
}
