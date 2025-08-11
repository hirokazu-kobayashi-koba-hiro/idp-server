/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.core.openid.plugin.clientauthenticator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.oauth.clientauthenticator.plugin.ClientAuthenticator;
import org.idp.server.core.openid.oauth.type.oauth.ClientAuthenticationType;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.plugin.PluginLoader;

public class ClientAuthenticationPluginLoader extends PluginLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(ClientAuthenticationPluginLoader.class);

  public static Map<ClientAuthenticationType, ClientAuthenticator> load() {
    Map<ClientAuthenticationType, ClientAuthenticator> map = new HashMap<>();

    List<ClientAuthenticator> internals = loadFromInternalModule(ClientAuthenticator.class);
    for (ClientAuthenticator clientAuthenticator : internals) {
      map.put(clientAuthenticator.type(), clientAuthenticator);
      log.info(
          "Dynamic Registered internal client authenticator {}", clientAuthenticator.type().name());
    }

    List<ClientAuthenticator> externals = loadFromExternalModule(ClientAuthenticator.class);
    for (ClientAuthenticator clientAuthenticator : externals) {
      map.put(clientAuthenticator.type(), clientAuthenticator);
      log.info(
          "Dynamic Registered external client authenticator {}", clientAuthenticator.type().name());
    }

    return map;
  }
}
