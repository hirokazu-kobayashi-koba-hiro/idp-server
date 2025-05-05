package org.idp.server.core.authentication;

import org.idp.server.core.identity.repository.UserQueryRepository;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public interface AuthenticationInteractor {

  AuthenticationInteractionRequestResult interact(
      Tenant tenant,
      AuthorizationIdentifier authorizationIdentifier,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      AuthenticationTransaction transaction,
      UserQueryRepository userQueryRepository);
}
