/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.authentication.interactors.webauthn;

import java.io.Serializable;
import org.idp.server.basic.json.JsonReadable;

public class WebAuthnChallenge implements Serializable, JsonReadable {

  String challenge;

  public WebAuthnChallenge() {}

  public WebAuthnChallenge(String challenge) {
    this.challenge = challenge;
  }

  public String challenge() {
    return challenge;
  }
}
