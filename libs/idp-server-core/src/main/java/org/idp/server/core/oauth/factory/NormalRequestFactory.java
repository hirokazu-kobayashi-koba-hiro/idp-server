package org.idp.server.core.oauth.factory;

import java.util.Set;
import org.idp.server.core.basic.jose.JoseContext;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.oauth.AuthorizationProfile;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.oauth.request.AuthorizationRequestBuilder;
import org.idp.server.core.oauth.request.OAuthRequestParameters;
import org.idp.server.core.type.oauth.Scopes;
import org.idp.server.core.type.oidc.MaxAge;

/** NormalRequestFactory */
public class NormalRequestFactory implements AuthorizationRequestFactory {

  @Override
  public AuthorizationRequest create(
      AuthorizationProfile profile,
      OAuthRequestParameters parameters,
      JoseContext joseContext,
      Set<String> filteredScopes,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration) {

    AuthorizationRequestBuilder builder = new AuthorizationRequestBuilder();
    builder.add(createIdentifier());
    builder.add(serverConfiguration.tenantIdentifier());
    builder.add(profile);
    builder.add(new Scopes(filteredScopes));
    builder.add(parameters.responseType());
    builder.add(parameters.clientId());
    builder.add(clientConfiguration.client());
    builder.add(parameters.redirectUri());
    builder.add(parameters.state());
    builder.add(parameters.responseMode());
    builder.add(parameters.nonce());
    builder.add(parameters.display());
    builder.add(parameters.prompts());
    if (parameters.hasMaxAge()) {
      builder.add(parameters.maxAge());
    } else {
      builder.add(new MaxAge(serverConfiguration.defaultMaxAge()));
    }
    builder.add(parameters.uiLocales());
    builder.add(parameters.idTokenHint());
    builder.add(parameters.loginHint());
    builder.add(parameters.acrValues());
    builder.add(parameters.claims());
    builder.add(parameters.request());
    builder.add(parameters.requestUri());
    builder.add(convertClaimsPayload(parameters.claims()));
    builder.add(parameters.codeChallenge());
    builder.add(parameters.codeChallengeMethod());
    builder.add(convertAuthorizationDetails(parameters.authorizationDetailsValue()));
    builder.add(parameters.customParams());
    return builder.build();
  }
}
