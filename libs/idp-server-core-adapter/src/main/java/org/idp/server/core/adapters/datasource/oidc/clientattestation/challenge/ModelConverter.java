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

package org.idp.server.core.adapters.datasource.oidc.clientattestation.challenge;

import java.time.LocalDateTime;
import java.util.Map;
import org.idp.server.core.openid.oauth.clientattestation.challenge.ClientAttestationChallenge;
import org.idp.server.platform.date.LocalDateTimeParser;

class ModelConverter {

  static ClientAttestationChallenge convert(Map<String, String> stringMap) {
    return new ClientAttestationChallenge(
        stringMap.get("challenge"),
        stringMap.get("tenant_id"),
        parseOrNull(stringMap.get("expires_at")),
        parseOrNull(stringMap.get("created_at")));
  }

  private static LocalDateTime parseOrNull(String value) {
    if (value == null || value.isEmpty()) {
      return null;
    }
    return LocalDateTimeParser.parse(value);
  }
}
