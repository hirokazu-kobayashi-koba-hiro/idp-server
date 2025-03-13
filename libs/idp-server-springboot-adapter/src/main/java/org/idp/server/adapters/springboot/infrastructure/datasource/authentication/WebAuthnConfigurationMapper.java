package org.idp.server.adapters.springboot.infrastructure.datasource.authentication;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.authenticators.webauthn.WebAuthnConfiguration;

@Mapper
public interface WebAuthnConfigurationMapper {

  WebAuthnConfiguration selectBy(@Param("tenantIdentifier") TenantIdentifier tenantIdentifier);
}
