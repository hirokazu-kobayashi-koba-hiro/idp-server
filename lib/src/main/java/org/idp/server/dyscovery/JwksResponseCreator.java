package org.idp.server.dyscovery;

import java.util.Map;
import org.idp.server.basic.jose.JwkInvalidException;
import org.idp.server.basic.jose.JwkParser;
import org.idp.server.configuration.ServerConfiguration;

public class JwksResponseCreator {

  public Map<String, Object> create(ServerConfiguration serverConfiguration) {
    try {
      String jwks = serverConfiguration.jwks();
      return JwkParser.parsePublicKeys(jwks);
    } catch (JwkInvalidException e) {
      throw new RuntimeException(e);
    }
  }
}
