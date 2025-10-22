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

package org.idp.server.core.adapters.datasource.audit.query;

import java.time.LocalDateTime;
import java.util.Map;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.date.LocalDateTimeParser;
import org.idp.server.platform.json.JsonNodeWrapper;

public class ModelConvertor {

  static AuditLog convert(Map<String, String> result) {

    String id = result.get("id");
    String type = result.get("type");
    String description = result.get("description");
    String tenantId = result.get("tenant_id");
    String clientId = result.get("client_id");
    String userId = result.get("user_id");
    String externalUserId = result.get("external_user_id");
    JsonNodeWrapper userPayload = JsonNodeWrapper.fromString(result.get("user_payload"));
    String targetResource = result.get("target_resource");
    String targetResourceAction = result.get("target_resource_action");
    JsonNodeWrapper request = JsonNodeWrapper.fromString(result.get("request_payload"));
    JsonNodeWrapper before = JsonNodeWrapper.fromString(result.get("before_payload"));
    JsonNodeWrapper after = JsonNodeWrapper.fromString(result.get("after_payload"));
    String outcomeResult = result.get("outcome_result");
    String outcomeReason = result.get("outcome_reason");
    String targetTenantId = result.get("target_tenant_id");
    String ipAddress = result.get("ip_address");
    String userAgent = result.get("user_agent");
    JsonNodeWrapper attributes = JsonNodeWrapper.fromString(result.get("attributes"));
    boolean dryRun = Boolean.parseBoolean(result.get("dry_run"));
    LocalDateTime createdAt = LocalDateTimeParser.parse(result.get("created_at"));
    return new AuditLog(
        id,
        type,
        description,
        tenantId,
        clientId,
        userId,
        externalUserId,
        userPayload,
        targetResource,
        targetResourceAction,
        request,
        before,
        after,
        outcomeResult,
        outcomeReason,
        targetTenantId,
        ipAddress,
        userAgent,
        attributes,
        dryRun,
        createdAt);
  }
}
