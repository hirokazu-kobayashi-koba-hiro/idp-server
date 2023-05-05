package org.idp.server.ciba.request;

import java.util.Set;
import org.idp.server.basic.jose.JoseContext;
import org.idp.server.ciba.CibaProfile;
import org.idp.server.ciba.CibaRequestParameters;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.type.oauth.Scopes;

/** NormalRequestFactory */
public class NormalRequestFactory implements BackchannelAuthenticationRequestFactory {

  @Override
  public BackchannelAuthenticationRequest create(
      CibaProfile profile,
      CibaRequestParameters parameters,
      JoseContext joseContext,
      Set<String> filteredScopes,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration) {

    BackchannelAuthenticationRequestBuilder builder = new BackchannelAuthenticationRequestBuilder();
    builder.add(createIdentifier());
    builder.add(serverConfiguration.tokenIssuer());
    builder.add(profile);
    builder.add(new Scopes(filteredScopes));
    builder.add(clientConfiguration.clientId());
    builder.add(parameters.idTokenHint());
    builder.add(parameters.loginHint());
    builder.add(parameters.acrValues());
    builder.add(parameters.request());
    builder.add(parameters.clientNotificationToken());
    builder.add(parameters.userCode());
    builder.add(parameters.requestedExpiry());
    return builder.build();
  }
}
