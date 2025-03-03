package org.idp.server.core.ciba.request;

import java.util.Set;
import java.util.UUID;
import org.idp.server.core.basic.jose.JoseContext;
import org.idp.server.core.ciba.CibaProfile;
import org.idp.server.core.ciba.CibaRequestParameters;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;

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
