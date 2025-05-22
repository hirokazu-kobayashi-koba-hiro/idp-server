package org.idp.server.core.oidc.clientauthenticator.plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import org.idp.server.basic.type.oauth.ClientAuthenticationType;
import org.idp.server.platform.log.LoggerWrapper;

public class ClientAuthenticationPluginLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(ClientAuthenticationPluginLoader.class);

  public static Map<ClientAuthenticationType, ClientAuthenticator> load() {
    Map<ClientAuthenticationType, ClientAuthenticator> map = new HashMap<>();
    ServiceLoader<ClientAuthenticator> loader = ServiceLoader.load(ClientAuthenticator.class);
    for (ClientAuthenticator clientAuthenticator : loader) {
      map.put(clientAuthenticator.type(), clientAuthenticator);
      log.info("Dynamic Registered client authenticator {}", clientAuthenticator.type().name());
    }
    return map;
  }
}
