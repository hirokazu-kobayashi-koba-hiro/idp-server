package org.idp.server.adapters.springboot.subdomain.webauthn;

import org.idp.server.adapters.springboot.domain.model.tenant.Tenant;

public interface WebAuthnConfigurationRepository {
  WebAuthnConfiguration get(Tenant tenant);
}
