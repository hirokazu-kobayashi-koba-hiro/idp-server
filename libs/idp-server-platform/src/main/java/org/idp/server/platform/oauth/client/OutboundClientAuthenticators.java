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
package org.idp.server.platform.oauth.client;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Picks the authenticator for a credentials' type. */
public class OutboundClientAuthenticators {

  Map<OutboundClientAuthenticationType, OutboundClientAuthenticator> authenticators =
      new EnumMap<>(OutboundClientAuthenticationType.class);

  public OutboundClientAuthenticators() {
    for (OutboundClientAuthenticator authenticator :
        List.of(
            new ClientSecretBasicOutboundAuthenticator(),
            new ClientSecretPostOutboundAuthenticator(),
            new ClientSecretJwtOutboundAuthenticator(),
            new PrivateKeyJwtOutboundAuthenticator(),
            new NoneOutboundAuthenticator())) {
      authenticators.put(authenticator.type(), authenticator);
    }
  }

  public OutboundClientAuthentication authenticate(OutboundClientCredentials credentials) {
    return authenticators.get(credentials.type()).authenticate(credentials);
  }
}
