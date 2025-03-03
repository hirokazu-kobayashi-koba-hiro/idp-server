package org.idp.server.core.ciba.request;

import java.util.Set;
import org.idp.server.core.basic.jose.JoseContext;
import org.idp.server.core.ciba.CibaProfile;
import org.idp.server.core.ciba.CibaRequestParameters;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.type.oauth.Scopes;

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

    BackchannelAuthenticationRequestBuilder builder =
        new BackchannelAuthenticationRequestBuilder()
            .add(createIdentifier())
            .add(serverConfiguration.tokenIssuer())
            .add(profile)
            .add(clientConfiguration.backchannelTokenDeliveryMode())
            .add(new Scopes(filteredScopes))
            .add(clientConfiguration.clientId())
            .add(parameters.idTokenHint())
            .add(parameters.loginHint())
            .add(parameters.loginHintToken())
            .add(parameters.acrValues())
            .add(parameters.bindingMessage())
            .add(parameters.request())
            .add(parameters.clientNotificationToken())
            .add(parameters.userCode())
            .add(parameters.requestedExpiry());
    return builder.build();
  }
}
