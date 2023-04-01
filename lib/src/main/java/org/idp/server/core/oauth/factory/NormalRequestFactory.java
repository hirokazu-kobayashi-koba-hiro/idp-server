package org.idp.server.core.oauth.factory;

import java.util.UUID;
import org.idp.server.basic.jose.JoseContext;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.oauth.AuthorizationProfile;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.oauth.request.AuthorizationRequestBuilder;
import org.idp.server.core.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.type.*;

/** NormalRequestFactory */
public class NormalRequestFactory implements AuthorizationRequestFactory {

  @Override
  public AuthorizationRequest create(
      AuthorizationProfile profile,
      OAuthRequestParameters parameters,
      JoseContext joseContext,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration) {

    AuthorizationRequestBuilder builder = new AuthorizationRequestBuilder();
    builder.add(new AuthorizationRequestIdentifier(UUID.randomUUID().toString()));
    builder.add(serverConfiguration.issuer());
    builder.add(profile);
    builder.add(parameters.scope());
    builder.add(parameters.responseType());
    builder.add(parameters.clientId());
    builder.add(parameters.redirectUri());
    builder.add(parameters.state());
    builder.add(parameters.responseMode());
    builder.add(parameters.nonce());
    builder.add(parameters.display());
    builder.add(parameters.prompt());
    builder.add(parameters.maxAge());
    builder.add(parameters.uiLocales());
    builder.add(parameters.idTokenHint());
    builder.add(parameters.loginHint());
    builder.add(parameters.acrValues());
    builder.add(parameters.claims());
    builder.add(parameters.request());
    builder.add(parameters.requestUri());
    return builder.build();
  }
}
