package org.idp.server.adapters.springboot.application.service.authentication.internal;

import org.idp.server.adapters.springboot.domain.model.tenant.Tenant;
import org.idp.server.adapters.springboot.subdomain.webauthn.WebAuthnConfiguration;
import org.idp.server.adapters.springboot.subdomain.webauthn.WebAuthnConfigurationRepository;
import org.springframework.stereotype.Service;

@Service
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
