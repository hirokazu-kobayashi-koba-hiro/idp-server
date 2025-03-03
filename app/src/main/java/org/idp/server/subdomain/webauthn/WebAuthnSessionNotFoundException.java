package org.idp.server.subdomain.webauthn;

import org.idp.server.domain.model.base.NotFoundException;

public class WebAuthnSessionNotFoundException extends NotFoundException {
  public WebAuthnSessionNotFoundException(String message) {
    super(message);
  }
}
