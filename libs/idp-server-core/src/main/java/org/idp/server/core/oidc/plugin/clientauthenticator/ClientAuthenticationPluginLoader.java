/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.plugin.clientauthenticator;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import org.idp.server.basic.type.oauth.ClientAuthenticationType;
import org.idp.server.core.oidc.clientauthenticator.plugin.ClientAuthenticator;
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
