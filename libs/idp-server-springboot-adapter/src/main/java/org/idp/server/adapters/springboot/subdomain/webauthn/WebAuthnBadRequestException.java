package org.idp.server.adapters.springboot.subdomain.webauthn;

import org.idp.server.adapters.springboot.domain.model.base.BadRequestException;

public class WebAuthnBadRequestException extends BadRequestException {

  public WebAuthnBadRequestException(String message) {
    super(message);
  }

  public WebAuthnBadRequestException(String message, Throwable cause) {
    super(message, cause);
  }
}
