package org.idp.server.core.oidc.authentication.plugin;

import org.idp.server.core.oidc.authentication.AuthenticationInteractionType;
import org.idp.server.core.oidc.authentication.AuthenticationInteractor;

public interface AuthenticationInteractorFactory {

  AuthenticationInteractionType type();

  AuthenticationInteractor create(AuthenticationDependencyContainer container);
}
