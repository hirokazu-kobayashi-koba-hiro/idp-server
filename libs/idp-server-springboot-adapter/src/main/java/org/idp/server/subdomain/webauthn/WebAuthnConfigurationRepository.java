package org.idp.server.subdomain.webauthn;

import org.idp.server.domain.model.tenant.Tenant;

public interface WebAuthnConfigurationRepository {
  WebAuthnConfiguration get(Tenant tenant);
}
