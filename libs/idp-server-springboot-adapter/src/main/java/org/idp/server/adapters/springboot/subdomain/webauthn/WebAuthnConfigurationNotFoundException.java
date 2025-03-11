package org.idp.server.adapters.springboot.subdomain.webauthn;

import org.idp.server.adapters.springboot.domain.model.base.NotFoundException;

public class WebAuthnConfigurationNotFoundException extends NotFoundException {

  public WebAuthnConfigurationNotFoundException(String message) {
    super(message);
  }
}
