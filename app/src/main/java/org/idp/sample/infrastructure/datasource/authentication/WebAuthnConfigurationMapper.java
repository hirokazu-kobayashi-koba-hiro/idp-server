package org.idp.sample.infrastructure.datasource.authentication;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.idp.sample.domain.model.tenant.TenantIdentifier;
import org.idp.sample.subdomain.webauthn.WebAuthnConfiguration;

@Mapper
public interface WebAuthnConfigurationMapper {

  WebAuthnConfiguration selectBy(@Param("tenantIdentifier") TenantIdentifier tenantIdentifier);
}
