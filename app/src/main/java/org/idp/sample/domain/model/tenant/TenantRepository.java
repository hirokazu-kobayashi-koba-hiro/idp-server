package org.idp.sample.domain.model.tenant;

import org.idp.server.type.oauth.TokenIssuer;

public interface TenantRepository {

  Tenant get(TenantIdentifier tenantIdentifier);

  void register(Tenant tenant);

  void update(Tenant tenant);

  void delete(TenantIdentifier tenantIdentifier);

  Tenant find(TokenIssuer tokenIssuer);
}
