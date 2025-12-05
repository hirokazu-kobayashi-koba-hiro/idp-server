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

package org.idp.server.control_plane.management.statistics;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.statistics.TenantStatisticsQueries;
import org.idp.server.platform.type.RequestAttributes;

public class OrgTenantStatisticsContext implements AuditableContext {

  OrganizationIdentifier organizationIdentifier;
  TenantIdentifier tenantIdentifier;
  TenantStatisticsQueries queries;
  User operator;
  OAuthToken oAuthToken;
  RequestAttributes requestAttributes;

  public OrgTenantStatisticsContext(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      TenantStatisticsQueries queries,
      User operator,
      OAuthToken oAuthToken,
      RequestAttributes requestAttributes) {
    this.organizationIdentifier = organizationIdentifier;
    this.tenantIdentifier = tenantIdentifier;
    this.queries = queries;
    this.operator = operator;
    this.oAuthToken = oAuthToken;
    this.requestAttributes = requestAttributes;
  }

  @Override
  public String type() {
    return "tenant_statistics";
  }

  @Override
  public String description() {
    return "organization tenant statistics api";
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
  public Map<String, Object> request() {
    Map<String, Object> map = new HashMap<>();
    map.put("organization_id", organizationIdentifier.value());
    map.put("tenant_id", tenantIdentifier.value());
    if (queries != null) {
      map.putAll(queries.toMap());
    }
    return map;
  }

  @Override
  public Map<String, Object> before() {
    return Map.of();
  }

  @Override
  public Map<String, Object> after() {
    return Map.of();
  }

  @Override
  public String outcomeResult() {
    return "SUCCESS";
  }

  @Override
  public String outcomeReason() {
    return "";
  }

  @Override
  public String targetTenantId() {
    return tenantIdentifier.value();
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
  public Map<String, Object> attributes() {
    return Map.of();
  }

  @Override
  public boolean dryRun() {
    return false;
  }
}
