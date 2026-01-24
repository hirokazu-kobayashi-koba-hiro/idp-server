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

import org.idp.server.authenticators.webauthn4j.mds.MdsResolver;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutor;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutorFactory;
import org.idp.server.core.openid.authentication.plugin.AuthenticationDependencyContainer;
import org.idp.server.core.openid.authentication.repository.AuthenticationInteractionCommandRepository;
import org.idp.server.core.openid.authentication.repository.AuthenticationInteractionQueryRepository;

public class WebAuthn4jRegistrationExecutorFactory implements AuthenticationExecutorFactory {

  @Override
  public AuthenticationExecutor create(AuthenticationDependencyContainer container) {

    AuthenticationInteractionCommandRepository interactionCommandRepository =
        container.resolve(AuthenticationInteractionCommandRepository.class);
    AuthenticationInteractionQueryRepository interactionQueryRepository =
        container.resolve(AuthenticationInteractionQueryRepository.class);
    WebAuthn4jCredentialRepository credentialRepository =
        container.resolve(WebAuthn4jCredentialRepository.class);
    MdsResolver mdsResolver = container.resolve(MdsResolver.class);

    return new WebAuthn4jRegistrationExecutor(
        interactionCommandRepository,
        interactionQueryRepository,
        credentialRepository,
        mdsResolver);
  }
}
