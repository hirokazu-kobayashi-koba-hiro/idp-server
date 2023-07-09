package org.idp.server.type.verifiablecredential;

import java.util.Objects;

/**
 * transaction_id OPTIONAL.
 *
 * <p>OPTIONAL. A JSON string identifiying a Deferred Issuance transaction. This claim is contained
 * in the response, if the Credential Issuer was unable to immediately issue the credential. The
 * value is subsequently used to obtain the respective Credential with the Deferred Credential
 * Endpoint (see Section 9). MUST be present when credential parameter is not returned.
 *
 * @see <a
 *     href="https://openid.bitbucket.io/connect/openid-4-verifiable-credential-issuance-1_0.html#name-credential-response">7.3.
 *     Credential Response</a>
 */
public class TransactionId {
  String value;

  public TransactionId() {}

  public TransactionId(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TransactionId nonce = (TransactionId) o;
    return Objects.equals(value, nonce.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }
}
