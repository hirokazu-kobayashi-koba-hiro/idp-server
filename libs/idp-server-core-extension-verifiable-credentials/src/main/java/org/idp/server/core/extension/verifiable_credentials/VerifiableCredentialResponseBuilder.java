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


package org.idp.server.core.extension.verifiable_credentials;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.basic.type.verifiablecredential.CNonce;
import org.idp.server.basic.type.verifiablecredential.CNonceExpiresIn;
import org.idp.server.basic.type.verifiablecredential.Format;
import org.idp.server.basic.type.verifiablecredential.TransactionId;

public class VerifiableCredentialResponseBuilder {
  Format format;
  VerifiableCredential credentialJwt = new VerifiableCredential();
  CNonce cNonce = new CNonce();
  CNonceExpiresIn cNonceExpiresIn = new CNonceExpiresIn();
  TransactionId transactionId = new TransactionId();
  Map<String, Object> values = new HashMap<>();
  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  public VerifiableCredentialResponseBuilder() {}

  public VerifiableCredentialResponseBuilder add(Format format) {
    this.format = format;
    values.put("format", format.name());
    return this;
  }

  public VerifiableCredentialResponseBuilder add(VerifiableCredential credentialJwt) {
    this.credentialJwt = credentialJwt;
    values.put("credential", credentialJwt.value());
    return this;
  }

  public VerifiableCredentialResponseBuilder add(CNonce cNonce) {
    this.cNonce = cNonce;
    values.put("c_nonce", cNonce.value());
    return this;
  }

  public VerifiableCredentialResponseBuilder add(CNonceExpiresIn cNonceExpiresIn) {
    this.cNonceExpiresIn = cNonceExpiresIn;
    values.put("c_nonce_expires_in", cNonceExpiresIn.value());
    return this;
  }

  public VerifiableCredentialResponseBuilder add(TransactionId transactionId) {
    this.transactionId = transactionId;
    values.put("transaction_id", transactionId.value());
    return this;
  }

  public VerifiableCredentialResponse build() {
    String contents = jsonConverter.write(values);
    return new VerifiableCredentialResponse(
        format, credentialJwt, cNonce, cNonceExpiresIn, contents);
  }
}
