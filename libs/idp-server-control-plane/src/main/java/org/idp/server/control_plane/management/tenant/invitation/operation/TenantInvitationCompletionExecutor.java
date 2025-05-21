package org.idp.server.control_plane.management.tenant.invitation.operation;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.oidc.identity.event.*;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class TenantInvitationCompletionExecutor implements UserLifecycleEventExecutor {

  TenantInvitationCommandRepository tenantInvitationCommandRepository;
  TenantInvitationQueryRepository tenantInvitationQueryRepository;

  public TenantInvitationCompletionExecutor(
      TenantInvitationCommandRepository tenantInvitationCommandRepository,
      TenantInvitationQueryRepository tenantInvitationQueryRepository) {
    this.tenantInvitationCommandRepository = tenantInvitationCommandRepository;
    this.tenantInvitationQueryRepository = tenantInvitationQueryRepository;
  }

  @Override
  public UserLifecycleType lifecycleType() {
    return UserLifecycleType.INVITE_COMPLETE;
  }

  @Override
  public String name() {
    return "tenant-invitation-user-lifecycle-event-executor";
  }

  @Override
  public boolean shouldExecute(UserLifecycleEvent userLifecycleEvent) {
    UserLifecycleEventPayload payload = userLifecycleEvent.payload();
    return !payload.optValueAsString("invitation_id", "").isEmpty();
  }

  @Override
  public UserLifecycleEventResult execute(UserLifecycleEvent userLifecycleEvent) {
    UserLifecycleEventPayload payload = userLifecycleEvent.payload();
    Tenant tenant = userLifecycleEvent.tenant();
    TenantInvitationIdentifier identifier =
        new TenantInvitationIdentifier(payload.getValueAsString("invitation_id"));

    TenantInvitation tenantInvitation = tenantInvitationQueryRepository.find(tenant, identifier);

    if (!tenantInvitation.exists()) {
      Map<String, Object> response = new HashMap<>();
      response.put("invitation_id", identifier.value());
      response.put("message", "invitation id is not found");
      return UserLifecycleEventResult.failure(name(), response);
    }

    TenantInvitation updated = tenantInvitation.updateWithStatus("accepted");
    tenantInvitationCommandRepository.update(tenant, updated);

    Map<String, Object> response = new HashMap<>();
    response.put("invitation_id", identifier.value());
    response.put("message", "invitation update to accepted");
    return UserLifecycleEventResult.success(name(), response);
  }
}
