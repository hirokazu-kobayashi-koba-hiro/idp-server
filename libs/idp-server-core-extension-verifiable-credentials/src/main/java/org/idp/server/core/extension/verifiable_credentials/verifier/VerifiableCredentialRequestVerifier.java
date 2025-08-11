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

import org.idp.server.core.extension.verifiable_credentials.exception.VerifiableCredentialBadRequestException;
import org.idp.server.core.extension.verifiable_credentials.request.VerifiableCredentialProof;
import org.idp.server.core.extension.verifiable_credentials.request.VerifiableCredentialRequest;
import org.idp.server.core.extension.verifiable_credentials.request.VerifiableCredentialRequestTransformable;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;

public class VerifiableCredentialRequestVerifier
    implements VerifiableCredentialRequestTransformable {

  VerifiableCredentialRequest request;
  AuthorizationServerConfiguration authorizationServerConfiguration;

  public VerifiableCredentialRequestVerifier(
      VerifiableCredentialRequest request,
      AuthorizationServerConfiguration authorizationServerConfiguration) {
    this.request = request;
    this.authorizationServerConfiguration = authorizationServerConfiguration;
  }

  public void verify() {
    throwExceptionIfNotContainsRequiredParams();
    throwExceptionIfUnSupportedFormat();
    throwExceptionIfInvalidProof();
  }

  void throwExceptionIfNotContainsRequiredParams() {
    if (!request.isFormatDefined()) {
      throw new VerifiableCredentialBadRequestException(
          "invalid_request", "credential request must contains format");
    }
  }

  void throwExceptionIfUnSupportedFormat() {
    if (!authorizationServerConfiguration
        .credentialIssuerMetadata()
        .isSupportedFormat(request.format().value())) {
      throw new VerifiableCredentialBadRequestException(
          "invalid_request",
          String.format("unsupported credential format (%s)", request.format().value()));
    }
  }

  void throwExceptionIfInvalidProof() {
    if (!request.hasProof()) {
      return;
    }
    VerifiableCredentialProof verifiableCredentialProof = request.proof();
    if (!verifiableCredentialProof.isProofTypeDefined()) {
      throw new VerifiableCredentialBadRequestException(
          "invalid_request",
          "When credential request contains proof, proof entity must define proof_type");
    }
    if (verifiableCredentialProof.isJwtType() && !verifiableCredentialProof.hasJwt()) {
      throw new VerifiableCredentialBadRequestException(
          "invalid_request",
          "When credential request proof_type is jwt, proof entity must contains jwt claim");
    }
    if (verifiableCredentialProof.isCwtType() && !verifiableCredentialProof.hasJwt()) {
      throw new VerifiableCredentialBadRequestException(
          "invalid_request",
          "When credential request proof_type is cwt, proof entity must contains cwt claim");
    }
  }
}
