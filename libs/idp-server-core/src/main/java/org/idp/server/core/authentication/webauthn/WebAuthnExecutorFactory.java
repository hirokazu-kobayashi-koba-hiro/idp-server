package org.idp.server.core.authentication.webauthn;

import org.idp.server.core.authentication.AuthenticationDependencyContainer;

public interface WebAuthnExecutorFactory {

  WebAuthnExecutor create(AuthenticationDependencyContainer container);
}
