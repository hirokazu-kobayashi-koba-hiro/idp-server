/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


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
