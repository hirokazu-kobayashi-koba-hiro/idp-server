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

package org.idp.server.core.extension.identity.verified;

import java.util.Map;
import org.idp.server.core.extension.identity.verification.configuration.verified_claims.IdentityVerificationVerifiedClaimsConfiguration;
import org.idp.server.core.extension.identity.verification.io.IdentityVerificationApplicationRequest;
import org.idp.server.core.extension.identity.verification.io.IdentityVerificationCallbackRequest;
import org.idp.server.core.extension.identity.verification.io.IdentityVerificationRequest;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.idp.server.platform.mapper.MappingRuleObjectMapper;

public class VerifiedClaims {
  JsonNodeWrapper json;

  public VerifiedClaims() {
    this.json = JsonNodeWrapper.empty();
  }

  public VerifiedClaims(JsonNodeWrapper json) {
    this.json = json;
  }

  public static VerifiedClaims create(
      IdentityVerificationApplicationRequest request,
      IdentityVerificationVerifiedClaimsConfiguration verifiedClaimsConfiguration) {

    return create(request.toMap(), verifiedClaimsConfiguration);
  }

  public static VerifiedClaims createOnCallback(
      IdentityVerificationCallbackRequest request,
      IdentityVerificationVerifiedClaimsConfiguration verifiedClaimsConfiguration) {

    return create(request.toMap(), verifiedClaimsConfiguration);
  }

  public static VerifiedClaims create(
      IdentityVerificationRequest request,
      IdentityVerificationVerifiedClaimsConfiguration verifiedClaimsConfiguration) {

    return create(request.toMap(), verifiedClaimsConfiguration);
  }

  private static VerifiedClaims create(
      Map<String, Object> request,
      IdentityVerificationVerifiedClaimsConfiguration verifiedClaimsConfiguration) {
    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromMap(request);
    JsonPathWrapper jsonPath = new JsonPathWrapper(jsonNodeWrapper.toJson());
    Map<String, Object> mappingResult =
        MappingRuleObjectMapper.execute(verifiedClaimsConfiguration.mappingRules(), jsonPath);

    return new VerifiedClaims(JsonNodeWrapper.fromMap(mappingResult));
  }

  public String getValueOrEmptyAsString(String fieldName) {
    return json.getValueOrEmptyAsString(fieldName);
  }

  public Map<String, Object> toMap() {
    return json.toMap();
  }
}
