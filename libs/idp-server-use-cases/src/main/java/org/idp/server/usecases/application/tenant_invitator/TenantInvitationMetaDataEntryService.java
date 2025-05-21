package org.idp.server.usecases.application.tenant_invitator;

import java.util.Map;
import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.core.multi_tenancy.tenant.invitation.*;
import org.idp.server.platform.datasource.Transaction;

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
