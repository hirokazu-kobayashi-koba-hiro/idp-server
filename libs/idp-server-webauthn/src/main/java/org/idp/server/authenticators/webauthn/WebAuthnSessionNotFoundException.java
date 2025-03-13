package org.idp.server.authenticators.webauthn;

import org.idp.server.core.type.exception.NotFoundException;

public class WebAuthnSessionNotFoundException extends NotFoundException {
  public WebAuthnSessionNotFoundException(String message) {
    super(message);
  }
}
