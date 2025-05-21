package org.idp.server.core.ciba;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.idp.server.platform.dependency.protocol.AuthorizationProvider;
import org.idp.server.platform.exception.UnSupportedException;

public class CibaProtocols {

  Map<AuthorizationProvider, CibaProtocol> protocols;

  public CibaProtocols(Set<CibaProtocol> cibaProtocols) {
    Map<AuthorizationProvider, CibaProtocol> map = new HashMap<>();
    for (CibaProtocol cibaProtocol : cibaProtocols) {
      map.put(cibaProtocol.authorizationProtocolProvider(), cibaProtocol);
    }
    this.protocols = map;
  }

  public CibaProtocol get(AuthorizationProvider provider) {
    CibaProtocol cibaProtocol = protocols.get(provider);

    if (cibaProtocol == null) {
      throw new UnSupportedException("Unknown authorization provider " + provider.name());
    }

    return cibaProtocol;
  }
}
