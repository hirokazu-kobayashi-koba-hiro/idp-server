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
package org.idp.server.core.extension.oid4vci;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.idp.server.platform.dependency.protocol.AuthorizationProvider;
import org.idp.server.platform.exception.UnSupportedException;

public class Oid4vciProtocols {

  Map<AuthorizationProvider, Oid4vciProtocol> protocols;

  public Oid4vciProtocols(Set<Oid4vciProtocol> oid4vciProtocols) {
    Map<AuthorizationProvider, Oid4vciProtocol> map = new HashMap<>();
    for (Oid4vciProtocol protocol : oid4vciProtocols) {
      map.put(protocol.authorizationProtocolProvider(), protocol);
    }
    this.protocols = map;
  }

  public Oid4vciProtocol get(AuthorizationProvider provider) {
    Oid4vciProtocol protocol = protocols.get(provider);

    if (protocol == null) {
      throw new UnSupportedException("Unknown authorization provider " + provider.name());
    }

    return protocol;
  }
}
