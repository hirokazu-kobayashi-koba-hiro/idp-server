package org.idp.server.authentication.interactors.webauthn;

import org.idp.server.core.oidc.authentication.plugin.AuthenticationDependencyContainer;

public interface WebAuthnExecutorFactory {

  WebAuthnExecutor create(AuthenticationDependencyContainer container);
}
