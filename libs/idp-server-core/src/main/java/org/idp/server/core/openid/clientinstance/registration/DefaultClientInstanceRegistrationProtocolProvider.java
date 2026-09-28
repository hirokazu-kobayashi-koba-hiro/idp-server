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

package org.idp.server.core.openid.clientinstance.registration;

import org.idp.server.core.openid.clientinstance.ClientInstanceCommandRepository;
import org.idp.server.core.openid.clientinstance.ClientInstanceQueryRepository;
import org.idp.server.core.openid.clientinstance.registration.handler.ClientInstanceRegistrationHandler;
import org.idp.server.core.openid.clientinstance.registration.verifier.ClientInstanceRegistrationIdTokenVerifier;
import org.idp.server.core.openid.clientinstance.registration.verifier.ClientInstanceRegistrationPolicyVerifier;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.openid.plugin.clientinstance.PlatformAttestationVerifierPluginLoader;
import org.idp.server.platform.datasource.cache.CacheStore;
import org.idp.server.platform.dependency.ApplicationComponentContainer;
import org.idp.server.platform.dependency.protocol.ProtocolProvider;
import org.idp.server.platform.http.HttpRequestExecutor;

public class DefaultClientInstanceRegistrationProtocolProvider
    implements ProtocolProvider<ClientInstanceRegistrationProtocol> {

  @Override
  public Class<ClientInstanceRegistrationProtocol> type() {
    return ClientInstanceRegistrationProtocol.class;
  }

  @Override
  public ClientInstanceRegistrationProtocol provide(ApplicationComponentContainer container) {
    ClientConfigurationQueryRepository clientConfigurationQueryRepository =
        container.resolve(ClientConfigurationQueryRepository.class);
    ClientInstanceRegistrationChallengeRepository challengeRepository =
        container.resolve(ClientInstanceRegistrationChallengeRepository.class);
    UserQueryRepository userQueryRepository = container.resolve(UserQueryRepository.class);

    ClientInstanceRegistrationService registrationService =
        new ClientInstanceRegistrationService(
            challengeRepository,
            container.resolve(ClientInstanceQueryRepository.class),
            container.resolve(ClientInstanceCommandRepository.class),
            clientConfigurationQueryRepository,
            container.resolve(AuthorizationServerConfigurationQueryRepository.class),
            userQueryRepository,
            new ClientInstanceRegistrationIdTokenVerifier(),
            new PlatformAttestationVerifiers(
                PlatformAttestationVerifierPluginLoader.load(verifierDependencies(container))));

    ClientInstanceRegistrationHandler handler =
        new ClientInstanceRegistrationHandler(
            clientConfigurationQueryRepository,
            challengeRepository,
            new ClientInstanceRegistrationChallengeIssuer(),
            new ClientInstanceRegistrationPolicyVerifier(),
            registrationService);

    return new DefaultClientInstanceRegistrationProtocol(handler);
  }

  /**
   * The subset of the application's components that platform verifiers may build on.
   *
   * <p>Copied across one type at a time rather than passing {@code container} itself: verifiers are
   * loaded through {@code ServiceLoader}, {@code plugins/} jars included, and the application
   * container holds every repository in the system. See {@link
   * ClientInstanceRegistrationDependencyContainer}.
   */
  private ClientInstanceRegistrationDependencyContainer verifierDependencies(
      ApplicationComponentContainer container) {

    ClientInstanceRegistrationDependencyContainer dependencies =
        new ClientInstanceRegistrationDependencyContainer();
    dependencies.register(HttpRequestExecutor.class, container.resolve(HttpRequestExecutor.class));
    dependencies.register(CacheStore.class, container.resolve(CacheStore.class));
    return dependencies;
  }
}
