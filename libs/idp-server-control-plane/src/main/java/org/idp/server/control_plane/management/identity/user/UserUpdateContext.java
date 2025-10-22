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

package org.idp.server.control_plane.management.identity.user;

import java.util.Collections;
import java.util.Map;
import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.management.identity.user.io.UserManagementResponse;
import org.idp.server.control_plane.management.identity.user.io.UserManagementStatus;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.json.JsonDiffCalculator;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

public class UserUpdateContext implements AuditableContext {

  Tenant tenant;
  User operator;
  OAuthToken oAuthToken;
  RequestAttributes requestAttributes;
  User before;
  User after;
  Map<String, Object> requestPayload;
  boolean dryRun;

  public UserUpdateContext(
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      RequestAttributes requestAttributes,
      User before,
      User after,
      Map<String, Object> requestPayload,
      boolean dryRun) {
    this.tenant = tenant;
    this.operator = operator;
    this.oAuthToken = oAuthToken;
    this.requestAttributes = requestAttributes;
    this.before = before;
    this.after = after;
    this.requestPayload = requestPayload;
    this.dryRun = dryRun;
  }

  public Tenant tenant() {
    return tenant;
  }

  public User beforeUser() {
    return before;
  }

  public User afterUser() {
    return after;
  }

  // === AuditableContext Implementation ===

  @Override
  public String type() {
    return "UserManagementApi.update";
  }

  @Override
  public String description() {
    return "user";
  }

  @Override
  public String tenantId() {
    return tenant.identifier().value();
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
    return requestPayload;
  }

  @Override
  public Map<String, Object> before() {
    return before.toMaskedValueMap();
  }

  @Override
  public Map<String, Object> after() {
    return after.toMaskedValueMap();
  }

  @Override
  public String outcomeResult() {
    return "success";
  }

  @Override
  public String outcomeReason() {
    return null;
  }

  @Override
  public String targetTenantId() {
    return tenant.identifierValue();
  }

  @Override
  public Map<String, Object> attributes() {
    return Collections.emptyMap();
  }

  @Override
  public boolean dryRun() {
    return dryRun;
  }

  public UserManagementResponse toResponse() {
    JsonNodeWrapper beforeJson = JsonNodeWrapper.fromMap(before.toMap());
    JsonNodeWrapper afterJson = JsonNodeWrapper.fromMap(after.toMap());
    Map<String, Object> diff = JsonDiffCalculator.deepDiff(beforeJson, afterJson);
    Map<String, Object> contents = Map.of("result", after.toMap(), "diff", diff, "dry_run", dryRun);
    return new UserManagementResponse(UserManagementStatus.OK, contents);
  }
}
