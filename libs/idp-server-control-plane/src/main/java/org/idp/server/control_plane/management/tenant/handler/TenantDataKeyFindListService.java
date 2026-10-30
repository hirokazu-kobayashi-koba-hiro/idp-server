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
package org.idp.server.control_plane.management.tenant.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.control_plane.management.exception.InvalidRequestException;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.tenant.TenantManagementContextBuilder;
import org.idp.server.control_plane.management.tenant.io.TenantDataKeyFindListRequest;
import org.idp.server.control_plane.management.tenant.io.TenantManagementResponse;
import org.idp.server.control_plane.management.tenant.io.TenantManagementStatus;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.crypto.datakey.DataKeyPurposes;
import org.idp.server.platform.crypto.datakey.DataKeyService;
import org.idp.server.platform.crypto.datakey.DataKeyStatus;
import org.idp.server.platform.crypto.datakey.TenantDataKey;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Lists every generation of a tenant's data keys, without the key material.
 *
 * <p>Each key comes with how much of its purpose's data it still encrypts, as the purpose reports
 * it: an older generation at zero no longer holds anything. A tenant has a handful of keys, so they
 * are not paged.
 */
public class TenantDataKeyFindListService
    implements TenantManagementService<TenantDataKeyFindListRequest> {

  TenantQueryRepository tenantQueryRepository;
  DataKeyService dataKeyService;
  DataKeyPurposes dataKeyPurposes;

  public TenantDataKeyFindListService(
      TenantQueryRepository tenantQueryRepository,
      DataKeyService dataKeyService,
      DataKeyPurposes dataKeyPurposes) {
    this.tenantQueryRepository = tenantQueryRepository;
    this.dataKeyService = dataKeyService;
    this.dataKeyPurposes = dataKeyPurposes;
  }

  @Override
  public TenantManagementResponse execute(
      TenantManagementContextBuilder builder,
      Tenant adminTenant,
      User operator,
      OAuthToken oAuthToken,
      TenantDataKeyFindListRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    Tenant tenant = tenantQueryRepository.findWithDisabled(request.tenantIdentifier(), true);
    if (!tenant.exists()) {
      throw new ResourceNotFoundException(
          "Tenant not found: " + request.tenantIdentifier().value());
    }
    DataKeyStatus status = parseStatus(request.status());

    List<Map<String, Object>> list = new ArrayList<>();
    for (TenantDataKey key : dataKeyService.keys(tenant)) {
      if (request.purpose() != null && !request.purpose().equals(key.purpose())) {
        continue;
      }
      if (status != null && status != key.status()) {
        continue;
      }
      Map<String, Object> item = key.toMap();
      dataKeyPurposes
          .of(key.purpose())
          .ifPresent(purpose -> item.put(purpose.dataName(), purpose.countUnder(tenant, key.id())));
      list.add(item);
    }

    Map<String, Object> response = new HashMap<>();
    response.put("list", list);
    response.put("total_count", list.size());
    return new TenantManagementResponse(TenantManagementStatus.OK, response);
  }

  private DataKeyStatus parseStatus(String status) {
    if (status == null) {
      return null;
    }
    try {
      return DataKeyStatus.of(status);
    } catch (IllegalArgumentException exception) {
      throw new InvalidRequestException("status must be one of active, passive, destroyed.");
    }
  }
}
