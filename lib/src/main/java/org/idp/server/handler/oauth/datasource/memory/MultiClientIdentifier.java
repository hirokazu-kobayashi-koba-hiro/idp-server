package org.idp.server.handler.oauth.datasource.memory;

import java.util.Objects;
import org.idp.server.type.oauth.ClientId;
import org.idp.server.type.oauth.TokenIssuer;

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
