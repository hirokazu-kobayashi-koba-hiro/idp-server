package org.idp.server.core.authentication.device;

import java.util.Map;
import org.idp.server.core.authentication.*;
import org.idp.server.core.identity.repository.UserQueryRepository;
import org.idp.server.core.security.event.DefaultSecurityEventType;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

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
