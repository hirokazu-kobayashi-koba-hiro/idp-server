package org.idp.server.core.authentication.webauthn;

import java.util.Map;
import org.idp.server.basic.exception.UnSupportedException;

public class WebAuthnExecutors {

  Map<WebAuthnExecutorType, WebAuthnExecutor> executors;

  public WebAuthnExecutors(Map<WebAuthnExecutorType, WebAuthnExecutor> executors) {
    this.executors = executors;
  }

  public WebAuthnExecutor get(WebAuthnExecutorType type) {
    WebAuthnExecutor executor = executors.get(type);

    if (executor == null) {
      throw new UnSupportedException("Unsupported web authn executor type: " + type);
    }

    return executor;
  }
}
