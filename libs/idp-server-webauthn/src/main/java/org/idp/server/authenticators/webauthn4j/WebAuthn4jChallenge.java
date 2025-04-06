package org.idp.server.authenticators.webauthn4j;

import com.webauthn4j.data.client.challenge.Challenge;
import com.webauthn4j.util.ArrayUtil;
import com.webauthn4j.util.AssertUtil;
import com.webauthn4j.util.Base64UrlUtil;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import org.idp.server.core.authentication.webauthn.WebAuthnChallenge;

public class WebAuthn4jChallenge implements Challenge {
  byte[] value;

  public WebAuthn4jChallenge(byte[] value) {
    AssertUtil.notNull(value, "value cannot be null");
    this.value = value;
  }

  public WebAuthn4jChallenge(String base64urlString) {
    AssertUtil.notNull(base64urlString, "base64urlString cannot be null");
    this.value = Base64UrlUtil.decode(base64urlString);
  }

  public static WebAuthn4jChallenge generate() {

    UUID uuid = UUID.randomUUID();
    long hi = uuid.getMostSignificantBits();
    long lo = uuid.getLeastSignificantBits();
    byte[] value = ByteBuffer.allocate(16).putLong(hi).putLong(lo).array();
    return new WebAuthn4jChallenge(value);
  }

  public byte[] getValue() {
    return ArrayUtil.clone(this.value);
  }

  public boolean equals(Object o) {
    if (this == o) {
      return true;
    } else if (o != null && this.getClass() == o.getClass()) {
      WebAuthn4jChallenge that = (WebAuthn4jChallenge) o;
      return Arrays.equals(this.value, that.value);
    } else {
      return false;
    }
  }

  public int hashCode() {
    return Arrays.hashCode(this.value);
  }

  public String toString() {
    return ArrayUtil.toHexString(this.value);
  }

  public String challengeAsString() {

    return Base64.getUrlEncoder().encodeToString(value);
  }

  public WebAuthnChallenge toWebAuthnChallenge() {
    return new WebAuthnChallenge(challengeAsString());
  }
}
