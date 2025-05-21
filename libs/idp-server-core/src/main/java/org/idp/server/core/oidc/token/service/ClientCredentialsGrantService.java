package org.idp.server.core.oidc.token.service;

import java.util.UUID;
import org.idp.server.basic.type.extension.CustomProperties;
import org.idp.server.basic.type.oauth.GrantType;
import org.idp.server.basic.type.oauth.Scopes;
import org.idp.server.core.oidc.clientcredentials.ClientCredentials;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.grant.AuthorizationGrant;
import org.idp.server.core.oidc.grant.AuthorizationGrantBuilder;
import org.idp.server.core.oidc.token.*;
import org.idp.server.core.oidc.token.repository.OAuthTokenRepository;
import org.idp.server.core.oidc.token.validator.ClientCredentialsGrantValidator;
import org.idp.server.core.oidc.token.verifier.ClientCredentialsGrantVerifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class ClientCredentialsGrantService
    implements OAuthTokenCreationService, AccessTokenCreatable {
  OAuthTokenRepository oAuthTokenRepository;

  public ClientCredentialsGrantService(OAuthTokenRepository oAuthTokenRepository) {
    this.oAuthTokenRepository = oAuthTokenRepository;
  }

  @Override
  public GrantType grantType() {
    return GrantType.client_credentials;
  }

  @Override
  public OAuthToken create(TokenRequestContext context, ClientCredentials clientCredentials) {
    ClientCredentialsGrantValidator validator = new ClientCredentialsGrantValidator(context);
    validator.validate();

    Tenant tenant = context.tenant();
    AuthorizationServerConfiguration authorizationServerConfiguration =
        context.serverConfiguration();
    ClientConfiguration clientConfiguration = context.clientConfiguration();

    Scopes scopes =
        new Scopes(clientConfiguration.filteredScope(context.scopes().toStringValues()));
    ClientCredentialsGrantVerifier verifier = new ClientCredentialsGrantVerifier(scopes);
    verifier.verify();

    CustomProperties customProperties = context.customProperties();
    AuthorizationGrant authorizationGrant =
        new AuthorizationGrantBuilder(
                context.tenantIdentifier(),
                context.requestedClientId(),
                GrantType.client_credentials,
                scopes)
            .add(customProperties)
            .add(clientConfiguration.client())
            .build();

    AccessToken accessToken =
        createAccessToken(
            authorizationGrant,
            authorizationServerConfiguration,
            clientConfiguration,
            clientCredentials);

    OAuthToken oAuthToken =
        new OAuthTokenBuilder(new OAuthTokenIdentifier(UUID.randomUUID().toString()))
            .add(accessToken)
            .build();

    oAuthTokenRepository.register(tenant, oAuthToken);
    return oAuthToken;
  }
}
