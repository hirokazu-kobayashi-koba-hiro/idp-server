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

package org.idp.server.core.openid.plugin.clientinstance;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.openid.clientinstance.registration.ClientInstanceRegistrationDependencyContainer;
import org.idp.server.core.openid.clientinstance.registration.PlatformAttestationVerifier;
import org.idp.server.core.openid.clientinstance.registration.PlatformAttestationVerifierFactory;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.plugin.PluginLoader;

/**
 * Loads {@link PlatformAttestationVerifier} implementations.
 *
 * <p>Nothing is registered by default: with no verifier every registration is rejected, which is
 * the safe direction for an unauthenticated endpoint. Platform verifiers are contributed by their
 * own modules. There is no bypass: tests attest with chains leading to a root they generate, which
 * the client under test trusts through {@code client_instance_platform_config}.
 *
 * <p>What the SPI registers is {@link PlatformAttestationVerifierFactory} rather than the verifier,
 * so that a verifier can be built with collaborators. See that interface for why.
 */
public class PlatformAttestationVerifierPluginLoader extends PluginLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(PlatformAttestationVerifierPluginLoader.class);

  public static List<PlatformAttestationVerifier> load(
      ClientInstanceRegistrationDependencyContainer container) {

    List<PlatformAttestationVerifier> verifiers = new ArrayList<>();
    for (PlatformAttestationVerifierFactory factory :
        loadFromInternalModule(PlatformAttestationVerifierFactory.class)) {
      verifiers.add(factory.create(container));
    }
    for (PlatformAttestationVerifierFactory factory :
        loadFromExternalModule(PlatformAttestationVerifierFactory.class)) {
      verifiers.add(factory.create(container));
    }

    verifiers.forEach(
        verifier ->
            log.info("Dynamic registered platform attestation verifier {}", verifier.platform()));

    if (verifiers.isEmpty()) {
      log.info(
          "No platform attestation verifier registered. Client instance registration will reject every request.");
    }

    return verifiers;
  }
}
