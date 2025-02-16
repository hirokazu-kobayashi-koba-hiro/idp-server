package org.idp.sample.infrastructure.datasource.tenant;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.idp.sample.domain.model.tenant.Tenant;
import org.idp.sample.domain.model.tenant.TenantIdentifier;
import org.idp.server.type.oauth.TokenIssuer;

@Mapper
public interface TenantMapper {

  Tenant selectBy(@Param("tenantIdentifier") TenantIdentifier tenantIdentifier);

  void insert(@Param("tenant") Tenant tenant);

  void update(@Param("tenant") Tenant tenant);

  void delete(@Param("tenantIdentifier") TenantIdentifier tenantIdentifier);

  Tenant selectByTokenIssuer(@Param("tokenIssuer") TokenIssuer tokenIssuer);

  Tenant selectAdmin();

}
