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

package org.idp.server.core.openid.grant_management.grant.consent;

import java.time.LocalDateTime;
import java.util.*;
import org.idp.server.platform.json.JsonReadable;

public class ConsentClaims implements JsonReadable {

  Map<String, List<ConsentClaim>> claims;

  public ConsentClaims() {
    this.claims = new HashMap<>();
  }

  public ConsentClaims(Map<String, List<ConsentClaim>> claims) {
    this.claims = claims;
  }

  public boolean exists() {
    return claims != null && !claims.isEmpty();
  }

  /**
   * Returns the raw claims map with ConsentClaim domain objects.
   *
   * <p>Used for DB persistence via JsonConverter, which has FIELD visibility enabled and can
   * serialize ConsentClaim's package-private fields directly. Note that LocalDateTime fields are
   * serialized as JSON arrays (e.g. [2026,4,1,12,0]) in this path.
   */
  public Map<String, List<ConsentClaim>> toMap() {
    return claims;
  }

  /**
   * Returns the claims as a plain Map structure for API response serialization.
   *
   * <p>Used for API responses (e.g. Grant Management API) where Spring Boot's default ObjectMapper
   * serializes the response. Since ConsentClaim has package-private fields with non-JavaBean
   * getters (name() instead of getName()), the default ObjectMapper cannot detect them and produces
   * {}. This method converts each ConsentClaim to a Map via {@link ConsentClaim#toMap()}, making it
   * serializable by any ObjectMapper. LocalDateTime is converted to ISO-8601 string format.
   *
   * @see <a href="https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/issues/1351">#1351</a>
   */
  public Map<String, List<Map<String, Object>>> toSerializableMap() {
    Map<String, List<Map<String, Object>>> result = new HashMap<>();
    for (Map.Entry<String, List<ConsentClaim>> entry : claims.entrySet()) {
      result.put(entry.getKey(), entry.getValue().stream().map(ConsentClaim::toMap).toList());
    }
    return result;
  }

  public ConsentClaims merge(ConsentClaims other) {
    Map<String, List<ConsentClaim>> merged = new HashMap<>(this.claims);

    for (Map.Entry<String, List<ConsentClaim>> entry : other.toEntry()) {
      String key = entry.getKey();
      List<ConsentClaim> incomingList = entry.getValue();

      List<ConsentClaim> existingList = merged.getOrDefault(key, new ArrayList<>());

      for (ConsentClaim incoming : incomingList) {
        Optional<ConsentClaim> match =
            existingList.stream().filter(existing -> existing.equals(incoming)).findFirst();

        if (match.isPresent()) {
          ConsentClaim existing = match.get();
          // consentedAt is old
          LocalDateTime oldestTime =
              existing.consentedAt().isBefore(incoming.consentedAt())
                  ? existing.consentedAt()
                  : incoming.consentedAt();
          existingList.remove(existing);
          existingList.add(new ConsentClaim(incoming.name(), incoming.value(), oldestTime));
        } else {
          existingList.add(incoming);
        }
      }

      merged.put(key, existingList);
    }

    return new ConsentClaims(merged);
  }

  public boolean isAllConsented(ConsentClaims requested) {

    return requested.toEntry().stream()
        .allMatch(
            entry -> {
              String key = entry.getKey();
              List<ConsentClaim> requestedClaims = entry.getValue();
              List<ConsentClaim> existingClaims = claims.get(key);
              if (existingClaims == null) return false;

              return requestedClaims.stream().allMatch(existingClaims::contains);
            });
  }

  public Set<Map.Entry<String, List<ConsentClaim>>> toEntry() {
    return claims.entrySet();
  }
}
