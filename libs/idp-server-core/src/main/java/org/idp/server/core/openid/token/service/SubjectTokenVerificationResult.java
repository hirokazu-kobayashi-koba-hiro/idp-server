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

package org.idp.server.core.openid.token.service;

import java.util.Collections;
import java.util.Map;
import org.idp.server.core.openid.oauth.configuration.client.AvailableFederation;

/**
 * SubjectTokenVerificationResult
 *
 * <p>Represents the verified result of a subject_token in Token Exchange (RFC 8693). Encapsulates
 * the federation, resolved subject, claim mapping, and raw claims from the external IdP regardless
 * of verification method (JWT signature or introspection).
 */
public class SubjectTokenVerificationResult {

  private final AvailableFederation federation;
  private final String subject;
  private final String subjectClaimMapping;
  private final Map<String, Object> claims;

  public SubjectTokenVerificationResult(
      AvailableFederation federation,
      String subject,
      String subjectClaimMapping,
      Map<String, Object> claims) {
    this.federation = federation;
    this.subject = subject;
    this.subjectClaimMapping = subjectClaimMapping;
    this.claims = claims != null ? claims : Collections.emptyMap();
  }

  public AvailableFederation federation() {
    return federation;
  }

  public String subject() {
    return subject;
  }

  public String subjectClaimMapping() {
    return subjectClaimMapping;
  }

  public String providerId() {
    return federation.resolveProviderId();
  }

  public Map<String, Object> claims() {
    return claims;
  }

  public boolean hasClaims() {
    return !claims.isEmpty();
  }
}
