package org.idp.server.core.ciba.request;

import java.util.Set;
import java.util.UUID;
import org.idp.server.basic.jose.JoseContext;
import org.idp.server.basic.type.oauth.ClientSecretBasic;
import org.idp.server.core.ciba.CibaProfile;
import org.idp.server.core.ciba.CibaRequestParameters;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;

public interface BackchannelAuthenticationRequestFactory {
  BackchannelAuthenticationRequest create(
      CibaProfile profile,
      ClientSecretBasic clientSecretBasic,
      CibaRequestParameters parameters,
      JoseContext joseContext,
      Set<String> filteredScopes,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration);

  default BackchannelAuthenticationRequestIdentifier createIdentifier() {
    return new BackchannelAuthenticationRequestIdentifier(UUID.randomUUID().toString());
  }
}
