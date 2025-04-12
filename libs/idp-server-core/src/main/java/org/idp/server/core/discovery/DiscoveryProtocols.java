package org.idp.server.core.discovery;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.idp.server.core.basic.protcol.AuthorizationProtocolProvider;
import org.idp.server.core.type.exception.UnSupportedException;

public class DiscoveryProtocols {

  Map<AuthorizationProtocolProvider, DiscoveryProtocol> protocols;

  public DiscoveryProtocols(Set<DiscoveryProtocol> discoveryProtocols) {
    Map<AuthorizationProtocolProvider, DiscoveryProtocol> map = new HashMap<>();
    for (DiscoveryProtocol discoveryProtocol : discoveryProtocols) {
      map.put(discoveryProtocol.authorizationProtocolProvider(), discoveryProtocol);
    }
    this.protocols = map;
  }

  public DiscoveryProtocol get(AuthorizationProtocolProvider provider) {
    DiscoveryProtocol discoveryProtocol = protocols.get(provider);

    if (discoveryProtocol == null) {
      throw new UnSupportedException("Unknown authorization provider " + provider.name());
    }

    return discoveryProtocol;
  }
}
