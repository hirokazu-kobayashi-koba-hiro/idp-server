/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
