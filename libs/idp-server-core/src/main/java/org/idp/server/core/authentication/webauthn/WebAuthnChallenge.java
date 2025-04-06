package org.idp.server.core.authentication.webauthn;

import java.io.Serializable;
import org.idp.server.core.basic.json.JsonReadable;

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
