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
