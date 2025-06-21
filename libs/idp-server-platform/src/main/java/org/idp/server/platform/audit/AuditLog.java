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


package org.idp.server.platform.audit;

import org.idp.server.platform.json.JsonNodeWrapper;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class AuditLog {

    String id;
    String type;
    String description;
    String tenantId;
    String clientId;
    String userId;
    String externalUserId;
    JsonNodeWrapper userPayload;
    String targetResource;
    String targetResourceAction;
    String ipAddress;
    String userAgent;
    JsonNodeWrapper before;
    JsonNodeWrapper after;
    JsonNodeWrapper attributes;
    boolean dryRun;
    LocalDateTime createdAt;

    public AuditLog() {}

    public AuditLog(String id, String type, String description, String tenantId, String clientId, String userId, String externalUserId, JsonNodeWrapper userPayload, String targetResource, String targetResourceAction, String ipAddress, String userAgent, JsonNodeWrapper before, JsonNodeWrapper after, JsonNodeWrapper attributes, boolean dryRun, LocalDateTime createdAt) {
        this.id = id;
        this.type = type;
        this.description = description;
        this.tenantId = tenantId;
        this.clientId = clientId;
        this.userId = userId;
        this.externalUserId = externalUserId;
        this.userPayload = userPayload;
        this.targetResource = targetResource;
        this.targetResourceAction = targetResourceAction;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.before = before;
        this.after = after;
        this.attributes = attributes;
        this.dryRun = dryRun;
        this.createdAt = createdAt;
    }

    public String id() {
        return id;
    }

    public AuditLogIdentifier identifier() {
        return new AuditLogIdentifier(id);
    }

    public String type() {
        return type;
    }

    public String description() {
        return description;
    }

    public String tenantId() {
        return tenantId;
    }

    public String clientId() {
        return clientId;
    }

    public String userId() {
        return userId;
    }

    public String externalUserId() {
        return externalUserId;
    }

    public JsonNodeWrapper userPayload() {
        return userPayload;
    }

    public String targetResource() {
        return targetResource;
    }

    public String targetResourceAction() {
        return targetResourceAction;
    }

    public String ipAddress() {
        return ipAddress;
    }

    public String userAgent() {
        return userAgent;
    }

    public JsonNodeWrapper before() {
        return before;
    }

    public JsonNodeWrapper after() {
        return after;
    }

    public JsonNodeWrapper attributes() {
        return attributes;
    }

    public boolean dryRun() {
        return dryRun;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("type", type);
        map.put("description", description);
        map.put("tenant_id", tenantId);
        map.put("client_id", clientId);
        map.put("user_od", userId);
        map.put("external_user_id", externalUserId);
        map.put("user_payload", userPayload.toMap());
        map.put("target_resource", targetResource);
        map.put("target_resource_action", targetResourceAction);
        map.put("ip_address", ipAddress);
        map.put("user_agent", userAgent);
        map.put("before", before.toMap());
        map.put("after", after.toMap());
        map.put("attributes", attributes.toMap());
        map.put("dry_run", dryRun);
        map.put("created_at", createdAt.toString());
        return map;

    }

    public boolean exists() {
        return id != null && !id.isEmpty();
    }
}
