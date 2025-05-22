/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.userinfo;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.idp.server.platform.dependency.protocol.AuthorizationProvider;
import org.idp.server.platform.exception.UnSupportedException;

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
