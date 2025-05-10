package org.idp.server.core.oidc.userinfo;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.idp.server.basic.dependency.protocol.AuthorizationProvider;
import org.idp.server.basic.exception.UnSupportedException;

public class UserinfoProtocols {

  Map<AuthorizationProvider, UserinfoProtocol> protocols;

  public UserinfoProtocols(Set<UserinfoProtocol> userinfoProtocols) {
    Map<AuthorizationProvider, UserinfoProtocol> map = new HashMap<>();
    for (UserinfoProtocol userinfoProtocol : userinfoProtocols) {
      map.put(userinfoProtocol.authorizationProtocolProvider(), userinfoProtocol);
    }
    this.protocols = map;
  }

  public UserinfoProtocol get(AuthorizationProvider provider) {
    UserinfoProtocol userinfoProtocol = protocols.get(provider);

    if (userinfoProtocol == null) {
      throw new UnSupportedException("Unknown authorization provider " + provider.name());
    }

    return userinfoProtocol;
  }
}
