package org.idp.server.basic.type.verifiablecredential;

import java.util.Objects;

/**
 * c_nonce_expires_in: OPTIONAL.
 *
 * <p>JSON integer denoting the lifetime in seconds of the c_nonce.
 *
 * @see <a
 *     href="https://openid.bitbucket.io/connect/openid-4-verifiable-credential-issuance-1_0.html#name-credential-response">7.3.
 *     Credential Response</a>
 */
public class CNonceExpiresIn {
  Long value;

  public CNonceExpiresIn() {}

  public CNonceExpiresIn(long value) {
    this.value = value;
  }

  public CNonceExpiresIn(String value) {
    if (Objects.isNull(value) || value.isEmpty()) {
      return;
    }
    this.value = Long.parseLong(value);
  }

  public long value() {
    return value;
  }

  public String toStringValue() {
    return String.valueOf(value);
  }

  public boolean exists() {
    return Objects.nonNull(value);
  }
}
