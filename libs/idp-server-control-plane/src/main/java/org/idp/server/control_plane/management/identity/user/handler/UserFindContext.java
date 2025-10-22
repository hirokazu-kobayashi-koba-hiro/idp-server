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

package org.idp.server.control_plane.management.identity.user.handler;

import java.util.Collections;
import java.util.Map;
import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Context for user find (read) operations.
 *
 * <p>Holds the user being retrieved and audit information for logging purposes.
 */
public class UserFindContext implements AuditableContext {

  private final Tenant tenant;
  private final User operator;
  private final OAuthToken oAuthToken;
  private final RequestAttributes requestAttributes;
  private final User user;
  private final boolean dryRun;

  public UserFindContext(
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      RequestAttributes requestAttributes,
      User user,
      boolean dryRun) {
    this.tenant = tenant;
    this.operator = operator;
    this.oAuthToken = oAuthToken;
    this.requestAttributes = requestAttributes;
    this.user = user;
    this.dryRun = dryRun;
  }

  public User user() {
    return user;
  }

  // === AuditableContext Implementation ===

  @Override
  public String type() {
    return "UserManagementApi.get";
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
    return Collections.emptyMap();
  }

  @Override
  public Map<String, Object> before() {
    return Collections.emptyMap();
  }

  @Override
  public Map<String, Object> after() {
    return user.toMaskedValueMap();
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
}
