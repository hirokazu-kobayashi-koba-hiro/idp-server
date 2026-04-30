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

package org.idp.server.core.openid.oauth.dpop;

import java.util.List;

/**
 * Validates the {@code DPoP} HTTP request header presence rules.
 *
 * <p>RFC 9449 Section 4.3 (Check 1): the request MUST NOT contain more than one DPoP header field.
 * This validator inspects the raw header values supplied by the transport layer and rejects
 * requests with multiple DPoP headers via {@link DPoPProofInvalidException}, which is mapped to the
 * {@code invalid_dpop_proof} error response.
 *
 * <p>Modeled after {@link org.idp.server.core.openid.oauth.validator.OAuthRequestValidator} for
 * consistency: validators encapsulate protocol-level rules and live in the core layer.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9449.html#section-4.3">RFC 9449 Section 4.3</a>
 */
public class DPoPHeaderValidator {

  List<String> headerValues;

  public DPoPHeaderValidator(List<String> headerValues) {
    this.headerValues = headerValues;
  }

  /**
   * Validates the DPoP header presence rules.
   *
   * @throws DPoPProofInvalidException when more than one DPoP header value is supplied
   */
  public void validate() {
    throwExceptionIfMultipleHeaders();
  }

  void throwExceptionIfMultipleHeaders() {
    if (headerValues != null && headerValues.size() > 1) {
      throw new DPoPProofInvalidException(
          "request contains multiple DPoP headers (RFC 9449 Section 4.3)");
    }
  }
}
