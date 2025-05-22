/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.token;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.idp.server.platform.dependency.protocol.AuthorizationProvider;
import org.idp.server.platform.exception.UnSupportedException;

public class TokenProtocols {

  Map<AuthorizationProvider, TokenProtocol> protocols;

  public TokenProtocols(Set<TokenProtocol> tokenProtocols) {
    Map<AuthorizationProvider, TokenProtocol> map = new HashMap<>();
    for (TokenProtocol tokenProtocol : tokenProtocols) {
      map.put(tokenProtocol.authorizationProtocolProvider(), tokenProtocol);
    }
    this.protocols = map;
  }

  public TokenProtocol get(AuthorizationProvider provider) {
    TokenProtocol tokenProtocol = protocols.get(provider);

    if (tokenProtocol == null) {
      throw new UnSupportedException("Unknown authorization provider " + provider.name());
    }

    return tokenProtocol;
  }
}
