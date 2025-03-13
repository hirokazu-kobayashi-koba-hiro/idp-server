package org.idp.server.authenticators.webauthn;

import org.idp.server.core.type.exception.NotFoundException;

public class WebAuthnConfigurationNotFoundException extends NotFoundException {

  public WebAuthnConfigurationNotFoundException(String message) {
    super(message);
  }
}
