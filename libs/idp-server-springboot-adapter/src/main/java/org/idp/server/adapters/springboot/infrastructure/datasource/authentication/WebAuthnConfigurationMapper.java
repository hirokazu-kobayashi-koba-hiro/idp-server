package org.idp.server.adapters.springboot.infrastructure.datasource.authentication;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.idp.server.adapters.springboot.domain.model.tenant.TenantIdentifier;
import org.idp.server.adapters.springboot.subdomain.webauthn.WebAuthnConfiguration;

@Mapper
public interface WebAuthnConfigurationMapper {

  WebAuthnConfiguration selectBy(@Param("tenantIdentifier") TenantIdentifier tenantIdentifier);
}
