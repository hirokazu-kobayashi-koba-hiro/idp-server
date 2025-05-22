/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.discovery;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.idp.server.platform.dependency.protocol.AuthorizationProvider;
import org.idp.server.platform.exception.UnSupportedException;

public class DiscoveryProtocols {

  Map<AuthorizationProvider, DiscoveryProtocol> protocols;

  public DiscoveryProtocols(Set<DiscoveryProtocol> discoveryProtocols) {
    Map<AuthorizationProvider, DiscoveryProtocol> map = new HashMap<>();
    for (DiscoveryProtocol discoveryProtocol : discoveryProtocols) {
      map.put(discoveryProtocol.authorizationProtocolProvider(), discoveryProtocol);
    }
    this.protocols = map;
  }

  public DiscoveryProtocol get(AuthorizationProvider provider) {
    DiscoveryProtocol discoveryProtocol = protocols.get(provider);

    if (discoveryProtocol == null) {
      throw new UnSupportedException("Unknown authorization provider " + provider.name());
    }

    return discoveryProtocol;
  }
}
