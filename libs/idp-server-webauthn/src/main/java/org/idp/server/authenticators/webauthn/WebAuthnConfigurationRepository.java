package org.idp.server.authenticators.webauthn;

import org.idp.server.core.tenant.Tenant;

public interface WebAuthnConfigurationRepository {
  WebAuthnConfiguration get(Tenant tenant);
}
