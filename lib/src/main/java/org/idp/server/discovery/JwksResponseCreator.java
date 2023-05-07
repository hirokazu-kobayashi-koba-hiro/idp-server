package org.idp.server.discovery;

import java.util.Map;
import org.idp.server.basic.jose.JwkInvalidException;
import org.idp.server.basic.jose.JwkParser;
import org.idp.server.configuration.ServerConfiguration;

public class JwksResponseCreator {

  ServerConfiguration serverConfiguration;

  public JwksResponseCreator(ServerConfiguration serverConfiguration) {
    this.serverConfiguration = serverConfiguration;
  }

  public Map<String, Object> create() {
    try {
      String jwks = serverConfiguration.jwks();
      return JwkParser.parsePublicKeys(jwks);
    } catch (JwkInvalidException e) {
      throw new RuntimeException(e);
    }
  }
}
