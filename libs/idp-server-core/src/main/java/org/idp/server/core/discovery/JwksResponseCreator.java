package org.idp.server.core.discovery;

import java.util.Map;
import org.idp.server.core.basic.jose.JsonWebKeyInvalidException;
import org.idp.server.core.basic.jose.JwkParser;
import org.idp.server.core.configuration.ServerConfiguration;

public class JwksResponseCreator {

  ServerConfiguration serverConfiguration;

  public JwksResponseCreator(ServerConfiguration serverConfiguration) {
    this.serverConfiguration = serverConfiguration;
  }

  public Map<String, Object> create() {
    try {
      String jwks = serverConfiguration.jwks();
      return JwkParser.parsePublicKeys(jwks);
    } catch (JsonWebKeyInvalidException e) {
      throw new RuntimeException(e);
    }
  }
}
