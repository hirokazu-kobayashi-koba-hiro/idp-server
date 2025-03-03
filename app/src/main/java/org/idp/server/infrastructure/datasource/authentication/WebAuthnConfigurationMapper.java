package org.idp.server.infrastructure.datasource.authentication;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.idp.server.domain.model.tenant.TenantIdentifier;
import org.idp.server.subdomain.webauthn.WebAuthnConfiguration;

@Mapper
public interface WebAuthnConfigurationMapper {

  WebAuthnConfiguration selectBy(@Param("tenantIdentifier") TenantIdentifier tenantIdentifier);
}
