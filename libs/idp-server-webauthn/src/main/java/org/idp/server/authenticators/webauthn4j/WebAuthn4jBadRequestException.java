package org.idp.server.authenticators.webauthn4j;

import org.idp.server.basic.exception.BadRequestException;

public class WebAuthn4jBadRequestException extends BadRequestException {

  public WebAuthn4jBadRequestException(String message) {
    super(message);
  }

  public WebAuthn4jBadRequestException(String message, Throwable cause) {
    super(message, cause);
  }
}
