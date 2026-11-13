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

package org.idp.server.federation.sso.oidc;

import java.util.Map;
import org.idp.server.platform.jose.JsonWebTokenClaims;

/**
 * Outcome of verifying an ID Token.
 *
 * <p>Carries the verified claims on success. Verification has already parsed the token, so a caller
 * that needs a claim out of it — the {@code sub} identifying the external account, for instance —
 * would otherwise parse the same JWT a second time.
 */
public class IdTokenVerificationResult {
  boolean result;
  Map<String, Object> data;
  JsonWebTokenClaims claims;

  public IdTokenVerificationResult(boolean result, Map<String, Object> data) {
    this(result, data, null);
  }

  public IdTokenVerificationResult(
      boolean result, Map<String, Object> data, JsonWebTokenClaims claims) {
    this.result = result;
    this.data = data;
    this.claims = claims;
  }

  public static IdTokenVerificationResult success(JsonWebTokenClaims claims) {
    return new IdTokenVerificationResult(true, Map.of(), claims);
  }

  public boolean isError() {
    return !result;
  }

  public Map<String, Object> data() {
    return data;
  }

  /** Verified claims. Present only on success. */
  public JsonWebTokenClaims claims() {
    return claims;
  }
}
