package org.idp.server.adapters.springboot.infrastructure.datasource.tenant;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.idp.server.core.type.oauth.TokenIssuer;
import org.idp.server.adapters.springboot.domain.model.tenant.Tenant;
import org.idp.server.adapters.springboot.domain.model.tenant.TenantIdentifier;

@Mapper
public interface TenantMapper {

  Tenant selectBy(@Param("tenantIdentifier") TenantIdentifier tenantIdentifier);

  void insert(@Param("tenant") Tenant tenant);

  void update(@Param("tenant") Tenant tenant);

  void delete(@Param("tenantIdentifier") TenantIdentifier tenantIdentifier);

  Tenant selectByTokenIssuer(@Param("tokenIssuer") TokenIssuer tokenIssuer);

  Tenant selectAdmin();
}
