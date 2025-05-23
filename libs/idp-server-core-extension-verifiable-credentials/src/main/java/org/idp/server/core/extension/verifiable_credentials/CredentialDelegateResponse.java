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

import org.idp.server.basic.vc.Credential;

public class CredentialDelegateResponse {
  VerifiableCredentialTransactionStatus status;
  Credential credential;

  CredentialDelegateResponse(VerifiableCredentialTransactionStatus status, Credential credential) {
    this.status = status;
    this.credential = credential;
  }

  public static CredentialDelegateResponse issued(Credential credential) {
    return new CredentialDelegateResponse(VerifiableCredentialTransactionStatus.issued, credential);
  }

  public static CredentialDelegateResponse pending() {
    return new CredentialDelegateResponse(
        VerifiableCredentialTransactionStatus.pending, new Credential());
  }

  public VerifiableCredentialTransactionStatus status() {
    return status;
  }

  public Credential credential() {
    return credential;
  }

  public boolean isPending() {
    return status.isPending();
  }

  public boolean isIssued() {
    return status.isIssued();
  }
}
