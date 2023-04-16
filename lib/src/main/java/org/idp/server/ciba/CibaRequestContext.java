package org.idp.server.ciba;

import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.type.oauth.TokenIssuer;

public class CibaRequestContext {

  ServerConfiguration serverConfiguration;
  ClientConfiguration clientConfiguration;

  public TokenIssuer tokenIssuer() {
    return null;
  }
}
