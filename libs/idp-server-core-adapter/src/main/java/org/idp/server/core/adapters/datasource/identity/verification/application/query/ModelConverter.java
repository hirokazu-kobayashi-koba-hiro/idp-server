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

package org.idp.server.core.adapters.datasource.identity.verification.application.query;

import java.time.LocalDateTime;
import java.util.*;
import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.core.extension.identity.verification.application.model.*;
import org.idp.server.core.oidc.identity.UserIdentifier;
import org.idp.server.core.oidc.type.oauth.RequestedClientId;
import org.idp.server.platform.date.LocalDateTimeParser;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class ModelConverter {

  static IdentityVerificationApplication convert(Map<String, String> map) {

    IdentityVerificationApplicationIdentifier identifier =
        new IdentityVerificationApplicationIdentifier(map.get("id"));
    TenantIdentifier tenantIdentifier = new TenantIdentifier(map.get("tenant_id"));
    RequestedClientId requestedClientId = new RequestedClientId(map.get("client_id"));
    IdentityVerificationType verificationType =
        new IdentityVerificationType(map.get("verification_type"));
    UserIdentifier sub = new UserIdentifier(map.get("user_id"));
    IdentityVerificationApplicationDetails details =
        IdentityVerificationApplicationDetails.fromJson(map.get("application_details"));
    IdentityVerificationApplicationAttributes attributes =
        IdentityVerificationApplicationAttributes.fromJson(map.get("attributes"));

    IdentityVerificationApplicationProcessResults processes = toProcesses(map);

    IdentityVerificationApplicationStatus status =
        IdentityVerificationApplicationStatus.of(map.get("status"));
    LocalDateTime requestedAt = LocalDateTimeParser.parse(map.get("requested_at"));

    return new IdentityVerificationApplication(
        identifier,
        verificationType,
        tenantIdentifier,
        requestedClientId,
        sub,
        details,
        processes,
        attributes,
        status,
        requestedAt);
  }

  static IdentityVerificationApplicationProcessResults toProcesses(Map<String, String> map) {

    if (map.containsKey("processes") && map.get("processes") != null) {

      HashMap<String, IdentityVerificationApplicationProcessResult> results = new HashMap<>();
      JsonNodeWrapper interactions = JsonNodeWrapper.fromString(map.get("processes"));

      for (Iterator<String> it = interactions.fieldNames(); it.hasNext(); ) {
        String interaction = it.next();
        JsonNodeWrapper node = interactions.getValueAsJsonNode(interaction);
        int callCount = node.getValueAsInt("call_count");
        int successCount = node.getValueAsInt("success_count");
        int failureCount = node.getValueAsInt("failure_count");
        IdentityVerificationApplicationProcessResult result =
            new IdentityVerificationApplicationProcessResult(callCount, successCount, failureCount);
        results.put(interaction, result);
      }

      return new IdentityVerificationApplicationProcessResults(results);
    }

    return new IdentityVerificationApplicationProcessResults();
  }
}
