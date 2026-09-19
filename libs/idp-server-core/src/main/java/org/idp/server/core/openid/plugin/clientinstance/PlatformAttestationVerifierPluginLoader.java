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
import org.idp.server.core.openid.clientinstance.registration.PlatformAttestationVerifier;
import org.idp.server.core.openid.clientinstance.registration.RequestHashBindingVerifier;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.plugin.PluginLoader;

/**
 * Loads {@link PlatformAttestationVerifier} implementations.
 *
 * <p>Nothing is registered by default: with no verifier every registration is rejected, which is
 * the safe direction for an unauthenticated endpoint. Platform verifiers are contributed by their
 * own modules, and the development bypass has to be added deliberately.
 */
public class PlatformAttestationVerifierPluginLoader extends PluginLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(PlatformAttestationVerifierPluginLoader.class);

  /**
   * Environment variable enabling the development verifier. It is read here rather than registered
   * through the SPI so that the bypass cannot be pulled in merely by having a module on the
   * classpath.
   */
  static final String DEVELOPMENT_VERIFIER_ENV = "IDP_SERVER_CLIENT_INSTANCE_DEVELOPMENT_VERIFIER";

  public static List<PlatformAttestationVerifier> load() {
    List<PlatformAttestationVerifier> verifiers =
        new ArrayList<>(loadFromInternalModule(PlatformAttestationVerifier.class));
    verifiers.addAll(loadFromExternalModule(PlatformAttestationVerifier.class));

    if (Boolean.parseBoolean(System.getenv(DEVELOPMENT_VERIFIER_ENV))) {
      verifiers.add(new RequestHashBindingVerifier());
      log.warn(
          "{} is enabled: client instances can be registered without application or device attestation."
              + " This must never be set in production.",
          DEVELOPMENT_VERIFIER_ENV);
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
