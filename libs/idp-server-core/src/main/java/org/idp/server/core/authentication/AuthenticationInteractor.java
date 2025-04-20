package org.idp.server.core.authentication;

import org.idp.server.core.oauth.identity.UserRepository;
import org.idp.server.core.tenant.Tenant;

public interface AuthenticationInteractor {

  AuthenticationInteractionRequestResult interact(
      Tenant tenant,
      AuthenticationTransactionIdentifier authenticationTransactionIdentifier,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      AuthenticationInteractionResult result,
      UserRepository userRepository);
}
