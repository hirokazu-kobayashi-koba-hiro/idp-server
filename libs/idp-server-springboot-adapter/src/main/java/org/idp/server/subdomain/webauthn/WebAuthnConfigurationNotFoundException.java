package org.idp.server.subdomain.webauthn;

import org.idp.server.domain.model.base.NotFoundException;

public class WebAuthnConfigurationNotFoundException extends NotFoundException {

  public WebAuthnConfigurationNotFoundException(String message) {
    super(message);
  }
}
