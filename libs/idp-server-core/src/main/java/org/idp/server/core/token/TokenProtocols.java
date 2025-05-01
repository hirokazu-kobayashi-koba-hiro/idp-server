package org.idp.server.core.token;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.idp.server.basic.dependency.protocol.AuthorizationProtocolProvider;
import org.idp.server.basic.exception.UnSupportedException;

public class TokenProtocols {

  Map<AuthorizationProtocolProvider, TokenProtocol> protocols;

  public TokenProtocols(Set<TokenProtocol> tokenProtocols) {
    Map<AuthorizationProtocolProvider, TokenProtocol> map = new HashMap<>();
    for (TokenProtocol tokenProtocol : tokenProtocols) {
      map.put(tokenProtocol.authorizationProtocolProvider(), tokenProtocol);
    }
    this.protocols = map;
  }

  public TokenProtocol get(AuthorizationProtocolProvider provider) {
    TokenProtocol tokenProtocol = protocols.get(provider);

    if (tokenProtocol == null) {
      throw new UnSupportedException("Unknown authorization provider " + provider.name());
    }

    return tokenProtocol;
  }
}
