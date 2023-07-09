package org.idp.server.type.verifiablecredential;

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
  long value;

  public CNonceExpiresIn() {}

  public CNonceExpiresIn(int value) {
    this.value = value;
  }

  public CNonceExpiresIn(long value) {
    this.value = value;
  }

  public CNonceExpiresIn(String value) {
    this.value = Long.parseLong(value);
  }

  public long value() {
    return value;
  }

  public String toStringValue() {
    return String.valueOf(value);
  }
}
