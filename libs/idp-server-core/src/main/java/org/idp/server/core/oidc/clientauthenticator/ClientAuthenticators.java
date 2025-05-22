package org.idp.server.core.oidc.clientauthenticator;

import static org.idp.server.basic.type.oauth.ClientAuthenticationType.*;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.type.oauth.ClientAuthenticationType;
import org.idp.server.core.oidc.clientauthenticator.plugin.ClientAuthenticator;
import org.idp.server.core.oidc.plugin.clientauthenticator.ClientAuthenticationPluginLoader;
import org.idp.server.platform.exception.UnSupportedException;

public class ClientAuthenticators {

  Map<ClientAuthenticationType, ClientAuthenticator> map = new HashMap<>();

  public ClientAuthenticators() {
    map.put(client_secret_basic, new ClientSecretBasicAuthenticator());
    map.put(client_secret_post, new ClientSecretPostAuthenticator());
    map.put(client_secret_jwt, new ClientSecretJwtAuthenticator());
    map.put(private_key_jwt, new PrivateKeyJwtAuthenticator());
    map.put(none, new PublicClientAuthenticator());
    Map<ClientAuthenticationType, ClientAuthenticator> loaded =
        ClientAuthenticationPluginLoader.load();
    map.putAll(loaded);
  }

  public ClientAuthenticator get(ClientAuthenticationType clientAuthenticationType) {
    ClientAuthenticator authenticator = map.get(clientAuthenticationType);

    if (authenticator == null) {
      throw new UnSupportedException(
          "unknown client authentication type " + clientAuthenticationType.name());
    }

    return authenticator;
  }
}
