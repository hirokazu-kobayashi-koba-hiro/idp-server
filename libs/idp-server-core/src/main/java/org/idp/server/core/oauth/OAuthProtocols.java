package org.idp.server.core.oauth;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.idp.server.core.basic.protcol.AuthorizationProtocolProvider;
import org.idp.server.core.type.exception.UnSupportedException;

public class OAuthProtocols {

  Map<AuthorizationProtocolProvider, OAuthProtocol> protocols;

  public OAuthProtocols(Set<OAuthProtocol> setProtocols) {
    HashMap<AuthorizationProtocolProvider, OAuthProtocol> map = new HashMap<>();
    for (OAuthProtocol oAuthProtocol : setProtocols) {
      map.put(oAuthProtocol.authorizationProtocolProvider(), oAuthProtocol);
    }
    this.protocols = map;
  }

  public OAuthProtocol get(AuthorizationProtocolProvider provider) {
    OAuthProtocol oAuthProtocol = protocols.get(provider);

    if (oAuthProtocol == null) {
      throw new UnSupportedException("Unknown authorization provider " + provider.name());
    }

    return oAuthProtocol;
  }
}
