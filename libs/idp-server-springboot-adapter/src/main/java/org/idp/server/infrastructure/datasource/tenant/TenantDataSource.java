package org.idp.server.infrastructure.datasource.tenant;

import java.util.Objects;
import org.idp.server.core.type.oauth.TokenIssuer;
import org.idp.server.domain.model.tenant.*;
import org.springframework.stereotype.Repository;

@Repository
public class TenantDataSource implements TenantRepository {

  TenantMapper mapper;

  public TenantDataSource(TenantMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Tenant get(TenantIdentifier tenantIdentifier) {
    Tenant tenant = mapper.selectBy(tenantIdentifier);
    if (Objects.isNull(tenant)) {
      throw new TenantNotFoundException(
          String.format("Tenant is not found (%s)", tenantIdentifier.value()));
    }
    return tenant;
  }

  @Override
  public Tenant getAdmin() {
    Tenant tenant = mapper.selectAdmin();
    if (Objects.isNull(tenant)) {
      throw new TenantNotFoundException("Admin Tenant is unregistered.");
    }
    return tenant;
  }

  @Override
  public void register(Tenant tenant) {
    mapper.insert(tenant);
  }

  @Override
  public void update(Tenant tenant) {
    mapper.update(tenant);
  }

  @Override
  public void delete(TenantIdentifier tenantIdentifier) {
    mapper.delete(tenantIdentifier);
  }

  @Override
  public Tenant find(TokenIssuer tokenIssuer) {
    Tenant tenant = mapper.selectByTokenIssuer(tokenIssuer);
    if (Objects.isNull(tenant)) {
      return new Tenant();
    }
    return tenant;
  }
}
