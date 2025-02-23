package org.idp.sample.subdomain.webauthn;

import org.idp.sample.domain.model.tenant.Tenant;

public interface WebAuthnConfigurationRepository {
  WebAuthnConfiguration get(Tenant tenant);
}
