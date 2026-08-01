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

package org.idp.server.core.openid.oauth.clientattestation;

import java.util.List;
import org.idp.server.core.openid.oauth.clientauthenticator.exception.ClientUnAuthorizedException;

/**
 * Validates the Client Attestation HTTP request header presence rules.
 *
 * <p>Per draft-ietf-oauth-attestation-based-client-auth-10 Section 7.1 (rule 1) and Section 7.2
 * (rule 1), there is precisely one {@code OAuth-Client-Attestation} HTTP request header field and
 * precisely one {@code OAuth-Client-Attestation-PoP} HTTP request header field in a request that
 * uses Attestation-Based Client Authentication. This validator rejects requests carrying more than
 * one of either header.
 *
 * <p>Absence of the headers is accepted here: the headers are only required when the client
 * authenticates with {@code attest_jwt_client_auth}, which is enforced by the authenticator, not by
 * this validator. Modeled after {@link org.idp.server.core.openid.oauth.dpop.DPoPHeaderValidator}.
 */
public class ClientAttestationHeaderValidator {

  List<String> attestationHeaderValues;
  List<String> popHeaderValues;

  public ClientAttestationHeaderValidator(
      List<String> attestationHeaderValues, List<String> popHeaderValues) {
    this.attestationHeaderValues = attestationHeaderValues;
    this.popHeaderValues = popHeaderValues;
  }

  /**
   * Validates the header presence rules.
   *
   * @throws ClientUnAuthorizedException when more than one header value is supplied for either
   *     header field
   */
  public void validate() {
    throwExceptionIfMultipleHeaders(
        attestationHeaderValues, ClientAttestationJwt.HEADER_NAME, "Section 7.1");
    throwExceptionIfMultipleHeaders(
        popHeaderValues, ClientAttestationPopJwt.HEADER_NAME, "Section 7.2");
  }

  void throwExceptionIfMultipleHeaders(
      List<String> headerValues, String headerName, String section) {
    if (headerValues != null && headerValues.size() > 1) {
      throw new ClientUnAuthorizedException(
          String.format(
              "request contains multiple %s headers (draft-ietf-oauth-attestation-based-client-auth %s)",
              headerName, section));
    }
  }
}
