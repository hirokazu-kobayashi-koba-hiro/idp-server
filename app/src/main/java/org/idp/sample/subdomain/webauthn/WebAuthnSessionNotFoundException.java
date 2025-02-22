package org.idp.sample.subdomain.webauthn;

import org.idp.sample.domain.model.base.NotFoundException;

public class WebAuthnSessionNotFoundException extends NotFoundException {
  public WebAuthnSessionNotFoundException(String message) {
    super(message);
  }
}
