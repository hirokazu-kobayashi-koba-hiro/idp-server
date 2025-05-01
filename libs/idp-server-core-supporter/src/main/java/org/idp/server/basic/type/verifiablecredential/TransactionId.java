package org.idp.server.basic.type.verifiablecredential;

import java.util.Objects;

/**
 * transaction_id: REQUIRED.
 *
 * <p>JSON String identifying a Deferred Issuance transaction.
 *
 * @see <a
 *     href=https://openid.bitbucket.io/connect/openid-4-verifiable-credential-issuance-1_0.html#name-deferred-credential-request">9.1.
 *     Deferred Credential Request</a>
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
