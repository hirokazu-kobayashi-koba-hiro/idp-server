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
