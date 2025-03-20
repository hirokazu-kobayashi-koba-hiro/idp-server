package org.idp.server.core.adapters.datasource.configuration.memory;

import java.util.Objects;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.type.oauth.ClientId;

class MultiClientIdentifier {
  TenantIdentifier tenantIdentifier;
  ClientId clientId;

  public MultiClientIdentifier(TenantIdentifier tenantIdentifier, ClientId clientId) {
    this.tenantIdentifier = tenantIdentifier;
    this.clientId = clientId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MultiClientIdentifier that = (MultiClientIdentifier) o;
    return Objects.equals(tenantIdentifier, that.tenantIdentifier)
        && Objects.equals(clientId, that.clientId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(tenantIdentifier, clientId);
  }
}
