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

package org.idp.server.usecases.application.tenant_invitator;

import java.util.Map;
import org.idp.server.control_plane.management.tenant.invitation.operation.*;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.security.type.RequestAttributes;

@Transaction(readOnly = true)
public class TenantInvitationMetaDataEntryService implements TenantInvitationMetaDataApi {

  TenantInvitationQueryRepository tenantInvitationQueryRepository;
  TenantQueryRepository tenantQueryRepository;

  public TenantInvitationMetaDataEntryService(
      TenantInvitationQueryRepository tenantInvitationQueryRepository,
      TenantQueryRepository tenantQueryRepository) {
    this.tenantInvitationQueryRepository = tenantInvitationQueryRepository;
    this.tenantQueryRepository = tenantQueryRepository;
  }

  @Override
  public TenantInvitationMetaDataResponse get(
      TenantIdentifier tenantIdentifier,
      TenantInvitationIdentifier identifier,
      RequestAttributes requestAttributes) {
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    TenantInvitation tenantInvitation = tenantInvitationQueryRepository.find(tenant, identifier);
    if (!tenantInvitation.exists()) {
      return new TenantInvitationMetaDataResponse(
          TenantInvitationMetaDataStatus.NOT_FOUND, Map.of());
    }

    return new TenantInvitationMetaDataResponse(
        TenantInvitationMetaDataStatus.OK, tenantInvitation.toMap());
  }
}
