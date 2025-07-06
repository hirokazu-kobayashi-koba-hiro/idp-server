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

package org.idp.server.core.extension.identity.verification.result;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.idp.server.platform.date.LocalDateTimeParser;
import org.idp.server.platform.uuid.UuidConvertable;

public class IdentityVerificationResultQueries implements UuidConvertable {

  Map<String, String> values;

  public IdentityVerificationResultQueries() {}

  public IdentityVerificationResultQueries(Map<String, String> values) {
    this.values = values;
  }

  public LocalDateTime verifiedAtFrom() {
    return LocalDateTimeParser.parse(values.get("verified_at_from"));
  }

  public LocalDateTime to() {
    return LocalDateTimeParser.parse(values.get("verified_at_to"));
  }

  public boolean hasVerifiedAtFrom() {
    return values.containsKey("verified_at_from");
  }

  public boolean hasVerifiedAtTo() {
    return values.containsKey("verified_at_to");
  }

  public LocalDateTime verifiedUntilFrom() {
    return LocalDateTimeParser.parse(values.get("verified_until_from"));
  }

  public LocalDateTime verifiedUntilTo() {
    return LocalDateTimeParser.parse(values.get("verified_until_to"));
  }

  public boolean hasVerifiedUntilFrom() {
    return values.containsKey("verified_until_from");
  }

  public boolean hasVerifiedUntilTo() {
    return values.containsKey("verified_until_to");
  }

  public boolean hasId() {
    return values.containsKey("id");
  }

  public String id() {
    return values.get("id");
  }

  public UUID idAsUuid() {
    return convertUuid(id());
  }

  public boolean hasType() {
    return values.containsKey("type");
  }

  public String type() {
    return values.get("type");
  }

  public boolean hasApplicationId() {
    return values.containsKey("application_id");
  }

  public String applicationId() {
    return values.get("application_id");
  }

  public UUID applicationIdAsUuid() {
    return convertUuid(applicationId());
  }

  public boolean hasExternalApplicationId() {
    return values.containsKey("external_application_id");
  }

  public String externalApplicationId() {
    return values.get("external_application_id");
  }

  public boolean hasExternalService() {
    return values.containsKey("external_service");
  }

  public String externalService() {
    return values.get("external_service");
  }

  public boolean hasSource() {
    return values.containsKey("source");
  }

  public String source() {
    return values.get("source");
  }

  public boolean hasVerifiedClaims() {
    return !verifiedClaims().isEmpty();
  }

  public Map<String, String> verifiedClaims() {
    Map<String, String> details = new HashMap<>();

    for (Map.Entry<String, String> entry : values.entrySet()) {
      String key = entry.getKey();
      if (key.startsWith("verified_claims.")) {
        String value = entry.getValue();
        details.put(key.replace("verified_claims.", ""), value);
      }
    }

    return details;
  }

  public int limit() {
    if (!values.containsKey("limit")) {
      return 20;
    }
    return Integer.parseInt(values.get("limit"));
  }

  public int offset() {
    if (!values.containsKey("offset")) {
      return 0;
    }
    return Integer.parseInt(values.get("offset"));
  }
}
