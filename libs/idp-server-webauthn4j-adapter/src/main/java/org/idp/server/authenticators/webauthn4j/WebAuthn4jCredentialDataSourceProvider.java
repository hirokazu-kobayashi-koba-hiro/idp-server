/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.authenticators.webauthn4j;

import org.idp.server.authenticators.webauthn4j.datasource.credential.WebAuthn4jCredentialDataSource;
import org.idp.server.core.oidc.authentication.plugin.AuthenticationDependencyProvider;

public class WebAuthn4jCredentialDataSourceProvider
    implements AuthenticationDependencyProvider<WebAuthn4jCredentialRepository> {

  @Override
  public Class<WebAuthn4jCredentialRepository> type() {
    return WebAuthn4jCredentialRepository.class;
  }

  @Override
  public WebAuthn4jCredentialRepository provide() {
    return new WebAuthn4jCredentialDataSource();
  }
}
