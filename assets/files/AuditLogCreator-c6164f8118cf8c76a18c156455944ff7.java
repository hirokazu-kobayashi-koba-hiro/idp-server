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

package org.idp.server.control_plane.base;

import java.time.LocalDateTime;
import java.util.UUID;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.json.JsonNodeWrapper;

public class AuditLogCreator {

  public static AuditLog create(AuditableContext context) {
    String id = UUID.randomUUID().toString();
    LocalDateTime createdAt = SystemDateTime.now();

    return new AuditLog(
        id,
        context.type(),
        context.description(),
        context.tenantId(),
        context.clientId(),
        context.userId(),
        context.externalUserId(),
        JsonNodeWrapper.fromMap(context.userPayload()),
        context.targetResource(),
        context.targetResourceAction(),
        JsonNodeWrapper.fromMap(context.request()),
        JsonNodeWrapper.fromMap(context.before()),
        JsonNodeWrapper.fromMap(context.after()),
        context.outcomeResult(),
        context.outcomeReason(),
        context.targetTenantId(),
        context.ipAddress(),
        context.userAgent(),
        JsonNodeWrapper.fromMap(context.attributes()),
        context.dryRun(),
        createdAt);
  }
}
