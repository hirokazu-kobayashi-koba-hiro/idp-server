package org.idp.server.adapters.springboot.subdomain.webauthn;

import com.webauthn4j.data.client.challenge.Challenge;
import java.util.Base64;
import java.util.Objects;

public class WebAuthnSession {
  Challenge challenge;

  public WebAuthnSession() {}

  public WebAuthnSession(Challenge challenge) {
    this.challenge = challenge;
  }

  public Challenge challenge() {
    return challenge;
  }

  public boolean exists() {
    return Objects.nonNull(challenge);
  }

  public String challengeAsString() {

    return Base64.getUrlEncoder().encodeToString(challenge.getValue());
  }
}
