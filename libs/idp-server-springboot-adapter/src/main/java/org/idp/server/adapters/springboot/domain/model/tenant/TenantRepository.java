package org.idp.server.adapters.springboot.domain.model.tenant;

import org.idp.server.core.type.oauth.TokenIssuer;

public interface TenantRepository {

  Tenant get(TenantIdentifier tenantIdentifier);

  Tenant getAdmin();

  void register(Tenant tenant);

  void update(Tenant tenant);

  void delete(TenantIdentifier tenantIdentifier);

  Tenant find(TokenIssuer tokenIssuer);
}
