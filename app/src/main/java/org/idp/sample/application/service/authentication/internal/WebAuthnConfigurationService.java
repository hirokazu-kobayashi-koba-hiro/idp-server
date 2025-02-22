package org.idp.sample.application.service.authentication.internal;

import org.idp.sample.domain.model.tenant.Tenant;
import org.idp.sample.subdomain.webauthn.WebAuthnConfiguration;
import org.idp.sample.subdomain.webauthn.WebAuthnConfigurationRepository;
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
