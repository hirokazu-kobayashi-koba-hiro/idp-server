/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.authenticators.webauthn4j;

import org.idp.server.platform.exception.BadRequestException;

public class WebAuthn4jBadRequestException extends BadRequestException {

  public WebAuthn4jBadRequestException(String message) {
    super(message);
  }

  public WebAuthn4jBadRequestException(String message, Throwable cause) {
    super(message, cause);
  }
}
