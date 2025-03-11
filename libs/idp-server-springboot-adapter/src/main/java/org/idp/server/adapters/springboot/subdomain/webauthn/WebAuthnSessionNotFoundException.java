package org.idp.server.adapters.springboot.subdomain.webauthn;

import org.idp.server.adapters.springboot.domain.model.base.NotFoundException;

public class WebAuthnSessionNotFoundException extends NotFoundException {
  public WebAuthnSessionNotFoundException(String message) {
    super(message);
  }
}
