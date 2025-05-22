/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.authentication.interactors.webauthn;

import java.util.Map;
import org.idp.server.platform.exception.UnSupportedException;

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
