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
import java.util.Map;
import java.util.UUID;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.token.OAuthToken;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

public class AuditLogCreator {

  public static AuditLog create(
      String type,
      Tenant tenant,
      User user,
      OAuthToken oAuthToken,
      ConfigRegistrationContext context,
      RequestAttributes requestAttributes) {

    String id = UUID.randomUUID().toString();
    String description = context.type();
    String tenantId = tenant.identifier().value();
    String clientId = oAuthToken.requestedClientId().value();
    String userId = user.sub();
    String externalUserId = user.externalUserId();
    JsonNodeWrapper userPayload = JsonNodeWrapper.fromMap(user.toMap());
    String targetResource = requestAttributes.resource().value();
    String targetResourceAction = requestAttributes.action().value();
    String ipAddress = requestAttributes.getIpAddress().value();
    String userAgent = requestAttributes.getUserAgent().value();
    JsonNodeWrapper before = JsonNodeWrapper.empty();
    JsonNodeWrapper after = JsonNodeWrapper.fromMap(context.payload());
    JsonNodeWrapper attributes = JsonNodeWrapper.empty();
    boolean dryRun = context.isDryRun();
    LocalDateTime createdAt = SystemDateTime.now();
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
        ipAddress,
        userAgent,
        before,
        after,
        attributes,
        dryRun,
        createdAt);
  }

  public static AuditLog createOnRead(
      String type,
      String description,
      Tenant tenant,
      User user,
      OAuthToken oAuthToken,
      RequestAttributes requestAttributes) {

    String id = UUID.randomUUID().toString();
    String tenantId = tenant.identifier().value();
    String clientId = oAuthToken.requestedClientId().value();
    String userId = user.sub();
    String externalUserId = user.externalUserId();
    JsonNodeWrapper userPayload = JsonNodeWrapper.fromMap(user.toMap());
    String targetResource = requestAttributes.resource().value();
    String targetResourceAction = requestAttributes.action().value();
    String ipAddress = requestAttributes.getIpAddress().value();
    String userAgent = requestAttributes.getUserAgent().value();
    JsonNodeWrapper before = JsonNodeWrapper.empty();
    JsonNodeWrapper after = JsonNodeWrapper.empty();
    JsonNodeWrapper attributes = JsonNodeWrapper.empty();
    boolean dryRun = false;
    LocalDateTime createdAt = SystemDateTime.now();
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
        ipAddress,
        userAgent,
        before,
        after,
        attributes,
        dryRun,
        createdAt);
  }

  public static AuditLog createOnUpdate(
      String type,
      Tenant tenant,
      User user,
      OAuthToken oAuthToken,
      ConfigUpdateContext context,
      RequestAttributes requestAttributes) {

    String id = UUID.randomUUID().toString();
    String description = context.type();
    String tenantId = tenant.identifier().value();
    String clientId = oAuthToken.requestedClientId().value();
    String userId = user.sub();
    String externalUserId = user.externalUserId();
    JsonNodeWrapper userPayload = JsonNodeWrapper.fromMap(user.toMap());
    String targetResource = requestAttributes.resource().value();
    String targetResourceAction = requestAttributes.action().value();
    String ipAddress = requestAttributes.getIpAddress().value();
    String userAgent = requestAttributes.getUserAgent().value();
    JsonNodeWrapper before = JsonNodeWrapper.fromMap(context.beforePayload());
    JsonNodeWrapper after = JsonNodeWrapper.fromMap(context.afterPayload());
    JsonNodeWrapper attributes = JsonNodeWrapper.empty();
    boolean dryRun = context.isDryRun();
    LocalDateTime createdAt = SystemDateTime.now();
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
        ipAddress,
        userAgent,
        before,
        after,
        attributes,
        dryRun,
        createdAt);
  }

  public static AuditLog createOnDeletion(
      String type,
      String description,
      Tenant tenant,
      User user,
      OAuthToken oAuthToken,
      Map<String, Object> beforePayload,
      RequestAttributes requestAttributes) {

    String id = UUID.randomUUID().toString();
    String tenantId = tenant.identifier().value();
    String clientId = oAuthToken.requestedClientId().value();
    String userId = user.sub();
    String externalUserId = user.externalUserId();
    JsonNodeWrapper userPayload = JsonNodeWrapper.fromMap(user.toMap());
    String targetResource = requestAttributes.resource().value();
    String targetResourceAction = requestAttributes.action().value();
    String ipAddress = requestAttributes.getIpAddress().value();
    String userAgent = requestAttributes.getUserAgent().value();
    JsonNodeWrapper before = JsonNodeWrapper.fromMap(beforePayload);
    JsonNodeWrapper after = JsonNodeWrapper.empty();
    JsonNodeWrapper attributes = JsonNodeWrapper.empty();
    boolean dryRun = false;
    LocalDateTime createdAt = SystemDateTime.now();
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
        ipAddress,
        userAgent,
        before,
        after,
        attributes,
        dryRun,
        createdAt);
  }
}
