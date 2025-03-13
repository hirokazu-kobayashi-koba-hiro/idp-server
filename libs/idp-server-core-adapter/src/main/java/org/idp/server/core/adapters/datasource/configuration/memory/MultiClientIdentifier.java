package org.idp.server.core.adapters.datasource.configuration.memory;

import org.idp.server.core.type.oauth.ClientId;
import org.idp.server.core.type.oauth.TokenIssuer;

import java.util.Objects;

class MultiClientIdentifier {
  TokenIssuer tokenIssuer;
  ClientId clientId;

  public MultiClientIdentifier(TokenIssuer tokenIssuer, ClientId clientId) {
    this.tokenIssuer = tokenIssuer;
    this.clientId = clientId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MultiClientIdentifier that = (MultiClientIdentifier) o;
    return Objects.equals(tokenIssuer, that.tokenIssuer) && Objects.equals(clientId, that.clientId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(tokenIssuer, clientId);
  }
}
