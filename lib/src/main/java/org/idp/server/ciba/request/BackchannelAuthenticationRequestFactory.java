package org.idp.server.ciba.request;

import java.util.Set;
import java.util.UUID;
import org.idp.server.basic.jose.JoseContext;
import org.idp.server.ciba.CibaProfile;
import org.idp.server.ciba.CibaRequestParameters;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;

public interface BackchannelAuthenticationRequestFactory {
  BackchannelAuthenticationRequest create(
      CibaProfile profile,
      CibaRequestParameters parameters,
      JoseContext joseContext,
      Set<String> filteredScopes,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration);

  default BackchannelAuthenticationRequestIdentifier createIdentifier() {
    return new BackchannelAuthenticationRequestIdentifier(UUID.randomUUID().toString());
  }
}
