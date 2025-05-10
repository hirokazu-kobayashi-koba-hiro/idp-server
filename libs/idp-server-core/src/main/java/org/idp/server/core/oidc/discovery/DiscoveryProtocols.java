package org.idp.server.core.oidc.discovery;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.idp.server.basic.dependency.protocol.AuthorizationProvider;
import org.idp.server.basic.exception.UnSupportedException;

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
