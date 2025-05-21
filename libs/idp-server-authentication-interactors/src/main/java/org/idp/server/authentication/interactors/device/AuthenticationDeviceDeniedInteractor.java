package org.idp.server.authentication.interactors.device;

import java.util.Map;
import org.idp.server.core.oidc.authentication.*;
import org.idp.server.core.oidc.identity.repository.UserQueryRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.event.DefaultSecurityEventType;

public class AuthenticationDeviceDeniedInteractor implements AuthenticationInteractor {

  @Override
  public AuthenticationInteractionRequestResult interact(
      Tenant tenant,
      AuthorizationIdentifier authorizationIdentifier,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      AuthenticationTransaction transaction,
      UserQueryRepository userQueryRepository) {

    AuthenticationInteractionStatus status = AuthenticationInteractionStatus.SUCCESS;
    Map<String, Object> response = Map.of();
    DefaultSecurityEventType eventType =
        DefaultSecurityEventType.authentication_device_deny_success;
    return new AuthenticationInteractionRequestResult(status, type, response, eventType);
  }
}
