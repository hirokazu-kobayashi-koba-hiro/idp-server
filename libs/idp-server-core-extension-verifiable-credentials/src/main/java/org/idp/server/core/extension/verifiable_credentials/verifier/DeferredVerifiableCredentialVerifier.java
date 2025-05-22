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


package org.idp.server.core.extension.verifiable_credentials.verifier;

import org.idp.server.core.extension.verifiable_credentials.CredentialDelegateResponse;
import org.idp.server.core.extension.verifiable_credentials.exception.VerifiableCredentialBadRequestException;
import org.idp.server.core.extension.verifiable_credentials.request.VerifiableCredentialRequestTransformable;

public class DeferredVerifiableCredentialVerifier
    implements VerifiableCredentialRequestTransformable {

  CredentialDelegateResponse credentialDelegateResponse;

  public DeferredVerifiableCredentialVerifier(
      CredentialDelegateResponse credentialDelegateResponse) {
    this.credentialDelegateResponse = credentialDelegateResponse;
  }

  public void verify() {
    if (credentialDelegateResponse.isPending()) {
      throw new VerifiableCredentialBadRequestException(
          "issuance_pending", "The credential issuance is still pending");
    }
  }
}
