package org.idp.server.core.oidc.discovery;

import java.util.Map;
import org.idp.server.basic.jose.JsonWebKeyInvalidException;
import org.idp.server.basic.jose.JwkParser;
import org.idp.server.core.oidc.configuration.ServerConfiguration;

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
