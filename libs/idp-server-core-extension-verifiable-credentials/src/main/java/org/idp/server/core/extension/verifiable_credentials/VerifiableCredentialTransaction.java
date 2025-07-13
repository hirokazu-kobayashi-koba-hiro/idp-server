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

import java.util.Objects;
import org.idp.server.core.oidc.type.oauth.RequestedClientId;
import org.idp.server.core.oidc.type.oauth.Subject;
import org.idp.server.core.oidc.type.vc.Credential;
import org.idp.server.core.oidc.type.verifiablecredential.CredentialIssuer;
import org.idp.server.core.oidc.type.verifiablecredential.TransactionId;

public class VerifiableCredentialTransaction {
  TransactionId transactionId;
  CredentialIssuer credentialIssuer;
  RequestedClientId requestedClientId;
  Subject subject;
  Credential credential;
  VerifiableCredentialTransactionStatus status;

  public VerifiableCredentialTransaction() {}

  public VerifiableCredentialTransaction(
      TransactionId transactionId,
      CredentialIssuer credentialIssuer,
      RequestedClientId requestedClientId,
      Subject subject,
      Credential credential,
      VerifiableCredentialTransactionStatus status) {
    this.transactionId = transactionId;
    this.credentialIssuer = credentialIssuer;
    this.requestedClientId = requestedClientId;
    this.subject = subject;
    this.credential = credential;
    this.status = status;
  }

  public TransactionId transactionId() {
    return transactionId;
  }

  public CredentialIssuer credentialIssuer() {
    return credentialIssuer;
  }

  public RequestedClientId clientId() {
    return requestedClientId;
  }

  public Subject subject() {
    return subject;
  }

  public Credential verifiableCredential() {
    return credential;
  }

  public VerifiableCredentialTransactionStatus status() {
    return status;
  }

  public boolean exists() {
    return Objects.nonNull(transactionId) && transactionId.exists();
  }
}
