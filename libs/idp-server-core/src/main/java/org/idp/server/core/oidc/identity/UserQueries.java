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

package org.idp.server.core.oidc.identity;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.idp.server.platform.date.LocalDateTimeParser;
import org.idp.server.platform.uuid.UuidConvertable;

public class UserQueries implements UuidConvertable {
  Map<String, String> values;

  public UserQueries() {}

  public UserQueries(Map<String, String> values) {
    this.values = Objects.requireNonNullElseGet(values, HashMap::new);
  }

  public boolean hasFrom() {
    return values.containsKey("from");
  }

  public LocalDateTime from() {
    return LocalDateTimeParser.parse(values.get("from"));
  }

  public boolean hasTo() {
    return values.containsKey("to");
  }

  public LocalDateTime to() {
    return LocalDateTimeParser.parse(values.get("to"));
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

  public String userId() {
    return values.get("user_id");
  }

  public UUID userIdAsUuid() {
    return convertUuid(userId());
  }

  public boolean hasUserId() {
    return values.containsKey("user_id");
  }

  public String externalUserId() {
    return values.get("external_user_id");
  }

  public boolean hasExternalUserId() {
    return values.containsKey("external_user_id");
  }

  public boolean hasProviderId() {
    return values.containsKey("provider_id");
  }

  public String providerId() {
    return values.get("provider_id");
  }

  public boolean hasProviderUserId() {
    return values.containsKey("external_user_id");
  }

  public String providerUserId() {
    return values.get("external_user_id");
  }

  public boolean hasName() {
    return values.containsKey("name");
  }

  public String name() {
    return values.get("name");
  }

  public boolean hasGivenName() {
    return values.containsKey("given_name");
  }

  public String givenName() {
    return values.get("given_name");
  }

  public boolean hasFamilyName() {
    return values.containsKey("family_name");
  }

  public String familyName() {
    return values.get("family_name");
  }

  public boolean hasMiddleName() {
    return values.containsKey("middle_name");
  }

  public String middleName() {
    return values.get("middle_name");
  }

  public boolean hasNickname() {
    return values.containsKey("nickname");
  }

  public String nickname() {
    return values.get("nickname");
  }

  public boolean hasPreferredUsername() {
    return values.containsKey("preferred_username");
  }

  public String preferredUsername() {
    return values.get("preferred_username");
  }

  public boolean hasEmail() {
    return values.containsKey("email");
  }

  public String email() {
    return values.get("email");
  }

  public boolean hasStatus() {
    return values.containsKey("status");
  }

  public UserStatus status() {
    return UserStatus.of(values.get("status"));
  }

  public boolean hasPhoneNumber() {
    return values.containsKey("phone_number");
  }

  public String phoneNumber() {
    return values.get("phone_number");
  }

  public boolean hasTenantId() {
    return values.containsKey("tenant_id");
  }

  public UUID tenantIdAsUuid() {
    return convertUuid(values.get("tenant_id"));
  }

  public boolean hasRole() {
    return values.containsKey("role");
  }

  public String role() {
    return values.get("role");
  }

  public boolean hasPermission() {
    return values.containsKey("permission");
  }

  public String permission() {
    return values.get("permission");
  }

  public boolean hasDetails() {
    return !details().isEmpty();
  }

  public Map<String, String> details() {
    Map<String, String> details = new HashMap<>();
    for (Map.Entry<String, String> entry : values.entrySet()) {
      String key = entry.getKey();
      if (key.startsWith("details.")) {
        String value = entry.getValue();
        details.put(key.replace("details.", ""), value);
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
