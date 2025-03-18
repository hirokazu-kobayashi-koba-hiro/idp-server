package org.idp.server.core.tenant;

import org.idp.server.core.configuration.ServerIdentifier;
import org.idp.server.core.type.oauth.TokenIssuer;

import java.util.HashMap;
import java.util.Map;

public class TenantServerAttribute {
  ServerIdentifier serverIdentifier;
  TokenIssuer tokenIssuer;

  public TenantServerAttribute() {}

  public TenantServerAttribute(ServerIdentifier serverIdentifier, TokenIssuer tokenIssuer) {
    this.serverIdentifier = serverIdentifier;
    this.tokenIssuer = tokenIssuer;
  }

  public ServerIdentifier serverIdentifier() {
    return serverIdentifier;
  }

  public TokenIssuer tokenIssuer() {
    return tokenIssuer;
  }

  public String serverIdentifierValue() {
    return serverIdentifier.value();
  }

  public String tokenIssuerValue() {
    return tokenIssuer.value();
  }

  public boolean exists() {
    return serverIdentifier != null && !serverIdentifier.exists();
  }

  public Map<String, Object> toMap() {
    if (!exists()) {
      return Map.of();
    }

    Map<String, Object> map = new HashMap<>();
    map.put("serverIdentifier", serverIdentifier.value());
    map.put("tokenIssuer", tokenIssuer.value());
    return map;
  }
}
