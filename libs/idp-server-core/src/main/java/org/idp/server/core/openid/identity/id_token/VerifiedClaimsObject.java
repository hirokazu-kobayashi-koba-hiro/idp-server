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

package org.idp.server.core.openid.identity.id_token;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.JsonReadable;

public class VerifiedClaimsObject implements JsonReadable {
  Map<String, Object> verification;
  Map<String, Object> claims;

  public VerifiedClaimsObject() {}

  public VerifiedClaimsObject(Map<String, Object> verification, Map<String, Object> claims) {
    this.verification = verification;
    this.claims = claims;
  }

  public JsonNodeWrapper verificationNodeWrapper() {
    return JsonNodeWrapper.fromMap(verification);
  }

  public JsonNodeWrapper claimsNodeWrapper() {
    return JsonNodeWrapper.fromMap(claims);
  }

  /**
   * Requested verified claim names (the keys of the OIDC4IDA {@code claims} member), e.g. {@code
   * given_name}, {@code family_name}. Used to surface which verified claims are being requested
   * (consent view data); returns an empty list when no verified claims are requested.
   */
  public List<String> requestedClaimNames() {
    if (claims == null || claims.isEmpty()) {
      return new ArrayList<>();
    }
    return new ArrayList<>(claims.keySet());
  }

  /**
   * A copy with the given claim names removed from the {@code claims} member (verification is
   * preserved), used to drop end-user-denied verified claims at grant build time (OIDC4IDA Section
   * 5.7.3). Returns this object unchanged when there is nothing to remove.
   */
  public VerifiedClaimsObject removeClaims(Collection<String> deniedClaimNames) {
    if (claims == null
        || claims.isEmpty()
        || deniedClaimNames == null
        || deniedClaimNames.isEmpty()) {
      return this;
    }
    Map<String, Object> filtered = new LinkedHashMap<>(claims);
    filtered.keySet().removeAll(deniedClaimNames);
    return new VerifiedClaimsObject(verification, filtered);
  }
}
