package org.idp.server.core.authentication.factory;

import org.idp.server.core.authentication.AuthenticationInteractionType;
import org.idp.server.core.authentication.AuthenticationInteractor;

public interface AuthenticationInteractorFactory {

  AuthenticationInteractionType type();

  AuthenticationInteractor create(AuthenticationDependencyContainer container);
}
