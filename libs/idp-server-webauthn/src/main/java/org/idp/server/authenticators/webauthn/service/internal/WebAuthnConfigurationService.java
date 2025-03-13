package org.idp.server.authenticators.webauthn.service.internal;

import org.idp.server.core.tenant.Tenant;
import org.idp.server.authenticators.webauthn.WebAuthnConfiguration;
import org.idp.server.authenticators.webauthn.WebAuthnConfigurationRepository;
public class WebAuthnConfigurationService {

  WebAuthnConfigurationRepository webAuthnConfigurationRepository;

  public WebAuthnConfigurationService(
      WebAuthnConfigurationRepository webAuthnConfigurationRepository) {
    this.webAuthnConfigurationRepository = webAuthnConfigurationRepository;
  }

  public WebAuthnConfiguration get(Tenant tenant) {

    return webAuthnConfigurationRepository.get(tenant);
  }
}
