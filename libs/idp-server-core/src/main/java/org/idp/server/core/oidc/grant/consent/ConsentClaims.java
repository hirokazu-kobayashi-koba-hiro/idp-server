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


package org.idp.server.core.oidc.grant.consent;

import java.time.LocalDateTime;
import java.util.*;
import org.idp.server.basic.json.JsonReadable;

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

  public Map<String, List<ConsentClaim>> toMap() {
    return claims;
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
