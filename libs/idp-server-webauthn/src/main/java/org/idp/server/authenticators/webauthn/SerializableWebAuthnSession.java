package org.idp.server.authenticators.webauthn;

import java.io.Serializable;
import org.idp.server.core.basic.json.JsonReadable;

public class SerializableWebAuthnSession implements Serializable, JsonReadable {
  String challenge;

  public SerializableWebAuthnSession() {}

  public SerializableWebAuthnSession(String challenge) {
    this.challenge = challenge;
  }

  public WebAuthnSession toWebAuthnSession() {

    WebAuthnChallenge webAuthnChallenge = new WebAuthnChallenge(challenge);
    return new WebAuthnSession(webAuthnChallenge);
  }
}
