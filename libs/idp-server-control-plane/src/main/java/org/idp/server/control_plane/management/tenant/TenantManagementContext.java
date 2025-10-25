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

package org.idp.server.control_plane.management.tenant;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.tenant.io.TenantManagementRequest;
import org.idp.server.control_plane.management.tenant.io.TenantManagementResponse;
import org.idp.server.control_plane.management.tenant.io.TenantManagementStatus;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.json.JsonDiffCalculator;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;

public class TenantManagementContext implements AuditableContext {

  TenantIdentifier tenantIdentifier;
  User operator;
  OAuthToken oAuthToken;
  RequestAttributes requestAttributes;
  Tenant before;
  Tenant after;
  TenantManagementRequest request;
  boolean dryRun;
  ManagementApiException exception;

  public TenantManagementContext(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      RequestAttributes requestAttributes,
      Tenant before,
      Tenant after,
      TenantManagementRequest request,
      boolean dryRun,
      ManagementApiException exception) {
    this.tenantIdentifier = tenantIdentifier;
    this.operator = operator;
    this.oAuthToken = oAuthToken;
    this.requestAttributes = requestAttributes;
    this.before = before;
    this.after = after;
    this.request = request;
    this.dryRun = dryRun;
    this.exception = exception;
  }

  public Tenant beforeTenant() {
    return before;
  }

  public Tenant afterTenant() {
    return after;
  }

  // === AuditableContext Implementation ===

  @Override
  public String type() {
    return "tenant";
  }

  @Override
  public String description() {
    return "tenant management api";
  }

  @Override
  public String tenantId() {
    return oAuthToken.tenantIdentifier().value();
  }

  @Override
  public String clientId() {
    return oAuthToken.requestedClientId().value();
  }

  @Override
  public String userId() {
    return operator.sub();
  }

  @Override
  public String externalUserId() {
    return operator.externalUserId();
  }

  @Override
  public Map<String, Object> userPayload() {
    return operator.toMap();
  }

  @Override
  public String targetResource() {
    return requestAttributes.resource().value();
  }

  @Override
  public String targetResourceAction() {
    return requestAttributes.action().value();
  }

  @Override
  public String ipAddress() {
    return requestAttributes.getIpAddress().value();
  }

  @Override
  public String userAgent() {
    return requestAttributes.getUserAgent().value();
  }

  @Override
  public Map<String, Object> request() {
    return request != null ? request.toMap() : Collections.emptyMap();
  }

  @Override
  public Map<String, Object> before() {
    return before != null ? before.toMap() : Collections.emptyMap();
  }

  @Override
  public Map<String, Object> after() {
    return after != null ? after.toMap() : Collections.emptyMap();
  }

  @Override
  public String outcomeResult() {
    return exception != null ? "failure" : "success";
  }

  @Override
  public String outcomeReason() {
    return exception != null ? exception.errorCode() : null;
  }

  @Override
  public String targetTenantId() {
    return tenantIdentifier.value();
  }

  @Override
  public Map<String, Object> attributes() {
    Map<String, Object> attributes = new HashMap<>();
    if (exception != null) {
      Map<String, Object> error = new HashMap<>();
      error.put("error_code", exception.errorCode());
      error.put("error_description", exception.errorDescription());

      // Add error details if available
      Map<String, Object> errorDetails = exception.errorDetails();
      if (errorDetails != null && !errorDetails.isEmpty()) {
        error.putAll(errorDetails);
      }
      attributes.put("error", error);
    }

    return attributes;
  }

  @Override
  public boolean dryRun() {
    return dryRun;
  }

  public Map<String, Object> diff() {
    JsonNodeWrapper beforeJson = JsonNodeWrapper.fromMap(before.toMap());
    JsonNodeWrapper afterJson = JsonNodeWrapper.fromMap(after.toMap());
    return JsonDiffCalculator.deepDiff(beforeJson, afterJson);
  }

  public TenantManagementResponse toResponse() {
    JsonNodeWrapper beforeJson = JsonNodeWrapper.fromMap(before.toMap());
    JsonNodeWrapper afterJson = JsonNodeWrapper.fromMap(after.toMap());
    Map<String, Object> contents = new HashMap<>();
    contents.put("result", after.toMap());
    contents.put("diff", JsonDiffCalculator.deepDiff(beforeJson, afterJson));
    contents.put("dry_run", dryRun);
    return new TenantManagementResponse(TenantManagementStatus.OK, contents);
  }
}
