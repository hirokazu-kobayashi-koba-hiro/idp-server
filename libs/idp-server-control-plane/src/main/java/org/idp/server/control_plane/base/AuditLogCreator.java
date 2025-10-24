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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

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
    JsonNodeWrapper request = JsonNodeWrapper.empty();
    JsonNodeWrapper before = JsonNodeWrapper.empty();
    JsonNodeWrapper after = JsonNodeWrapper.empty();
    String outcomeResult = "success";
    String outcomeReason = null;
    String targetTenantId = tenantId;
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
    JsonNodeWrapper request = JsonNodeWrapper.fromMap(context.afterPayload());
    JsonNodeWrapper before = JsonNodeWrapper.fromMap(context.beforePayload());
    JsonNodeWrapper after = JsonNodeWrapper.fromMap(context.afterPayload());
    String outcomeResult = "success";
    String outcomeReason = null;
    String targetTenantId = tenantId;
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
    JsonNodeWrapper request = JsonNodeWrapper.empty();
    JsonNodeWrapper before = JsonNodeWrapper.fromMap(beforePayload);
    JsonNodeWrapper after = JsonNodeWrapper.empty();
    String outcomeResult = "success";
    String outcomeReason = null;
    String targetTenantId = tenantId;
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

  public static AuditLog createOnError(
      String type,
      Tenant tenant,
      User user,
      OAuthToken oAuthToken,
      ManagementApiException exception,
      RequestAttributes requestAttributes) {

    String id = UUID.randomUUID().toString();
    String description = "failed: " + exception.errorCode();
    String tenantId = tenant.identifier().value();
    String clientId = oAuthToken.requestedClientId().value();
    String userId = user.sub();
    String externalUserId = user.externalUserId();
    JsonNodeWrapper userPayload = JsonNodeWrapper.fromMap(user.toMap());
    String targetResource = requestAttributes.resource().value();
    String targetResourceAction = requestAttributes.action().value();
    String ipAddress = requestAttributes.getIpAddress().value();
    String userAgent = requestAttributes.getUserAgent().value();
    JsonNodeWrapper request = JsonNodeWrapper.empty();
    JsonNodeWrapper before = JsonNodeWrapper.empty();
    JsonNodeWrapper after = JsonNodeWrapper.empty();
    String outcomeResult = "failure";
    String outcomeReason = exception.errorCode();
    String targetTenantId = tenantId;

    // Build error details in attributes
    Map<String, Object> errorAttributes = new HashMap<>();
    errorAttributes.put("error_code", exception.errorCode());
    errorAttributes.put("error_description", exception.errorDescription());
    errorAttributes.putAll(exception.errorDetails());
    JsonNodeWrapper attributes = JsonNodeWrapper.fromMap(errorAttributes);

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
