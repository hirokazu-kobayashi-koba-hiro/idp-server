/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.idp.server.platform.dependency.protocol.AuthorizationProvider;
import org.idp.server.platform.exception.UnSupportedException;

public class OAuthProtocols {

  Map<AuthorizationProvider, OAuthProtocol> protocols;

  public OAuthProtocols(Set<OAuthProtocol> setProtocols) {
    HashMap<AuthorizationProvider, OAuthProtocol> map = new HashMap<>();
    for (OAuthProtocol oAuthProtocol : setProtocols) {
      map.put(oAuthProtocol.authorizationProtocolProvider(), oAuthProtocol);
    }
    this.protocols = map;
  }

  public OAuthProtocol get(AuthorizationProvider provider) {
    OAuthProtocol oAuthProtocol = protocols.get(provider);

    if (oAuthProtocol == null) {
      throw new UnSupportedException("Unknown authorization provider " + provider.name());
    }

    return oAuthProtocol;
  }
}
