package org.idp.server.core.ciba;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.idp.server.core.basic.dependency.protcol.AuthorizationProtocolProvider;
import org.idp.server.core.type.exception.UnSupportedException;

public class CibaProtocols {

  Map<AuthorizationProtocolProvider, CibaProtocol> protocols;

  public CibaProtocols(Set<CibaProtocol> cibaProtocols) {
    Map<AuthorizationProtocolProvider, CibaProtocol> map = new HashMap<>();
    for (CibaProtocol cibaProtocol : cibaProtocols) {
      map.put(cibaProtocol.authorizationProtocolProvider(), cibaProtocol);
    }
    this.protocols = map;
  }

  public CibaProtocol get(AuthorizationProtocolProvider provider) {
    CibaProtocol cibaProtocol = protocols.get(provider);

    if (cibaProtocol == null) {
      throw new UnSupportedException("Unknown authorization provider " + provider.name());
    }

    return cibaProtocol;
  }
}
