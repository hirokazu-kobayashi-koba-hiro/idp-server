package org.idp.sample.subdomain.webauthn;

import org.idp.sample.domain.model.base.NotFoundException;

public class WebAuthnConfigurationNotFoundException extends NotFoundException {

  public WebAuthnConfigurationNotFoundException(String message) {
    super(message);
  }
}
