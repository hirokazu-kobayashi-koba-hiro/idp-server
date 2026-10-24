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

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.management.exception.InvalidRequestException;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.tenant.DataKeyPurposes;
import org.idp.server.control_plane.management.tenant.TenantManagementContextBuilder;
import org.idp.server.control_plane.management.tenant.io.TenantDataKeyRotateRequest;
import org.idp.server.control_plane.management.tenant.io.TenantManagementResponse;
import org.idp.server.control_plane.management.tenant.io.TenantManagementStatus;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.crypto.datakey.DataKeyService;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Starts a new generation of a tenant's data key, as the operations API does.
 *
 * <p>New data is encrypted under the new key and the previous one stays to open what it already
 * encrypted. Nothing is re-encrypted, so this is one row and safe to repeat.
 */
public class TenantDataKeyRotateService
    implements TenantManagementService<TenantDataKeyRotateRequest> {

  TenantQueryRepository tenantQueryRepository;
  DataKeyService dataKeyService;

  public TenantDataKeyRotateService(
      TenantQueryRepository tenantQueryRepository, DataKeyService dataKeyService) {
    this.tenantQueryRepository = tenantQueryRepository;
    this.dataKeyService = dataKeyService;
  }

  @Override
  public TenantManagementResponse execute(
      TenantManagementContextBuilder builder,
      Tenant adminTenant,
      User operator,
      OAuthToken oAuthToken,
      TenantDataKeyRotateRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    Tenant tenant = tenantQueryRepository.findWithDisabled(request.tenantIdentifier(), true);
    if (!tenant.exists()) {
      throw new ResourceNotFoundException(
          "Tenant not found: " + request.tenantIdentifier().value());
    }
    builder.withBefore(tenant);
    String purpose =
        request.purpose() == null ? DataKeyPurposes.DEFAULT : request.purpose().toString();
    if (!DataKeyPurposes.isKnown(purpose)) {
      throw new InvalidRequestException("purpose must be one of " + DataKeyPurposes.ALL + ".");
    }

    Map<String, Object> response = new HashMap<>();
    response.put("tenant_id", tenant.identifierValue());
    response.put("purpose", purpose);
    if (dryRun) {
      response.put("message", "Data key rotation simulated successfully");
      response.put("dry_run", true);
      return new TenantManagementResponse(TenantManagementStatus.OK, response);
    }

    response.put("data_key_id", dataKeyService.rotate(tenant, purpose));
    return new TenantManagementResponse(TenantManagementStatus.OK, response);
  }
}
