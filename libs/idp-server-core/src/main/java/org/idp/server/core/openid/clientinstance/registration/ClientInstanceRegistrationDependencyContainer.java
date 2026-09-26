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

import java.util.HashMap;
import java.util.Map;

/**
 * What a {@link PlatformAttestationVerifierFactory} may build its verifier from.
 *
 * <p>Deliberately not the application's own component container. Platform verifiers are loaded
 * through {@code ServiceLoader}, including from jars dropped in {@code plugins/}, so whatever this
 * holds is reachable by code the operator did not write. The application container holds every
 * repository in the system — handing that to an attestation verifier would give it the user store
 * to satisfy a need for an HTTP client.
 *
 * <p>Entries are therefore added one at a time, when a verifier has a reason for them:
 *
 * <ul>
 *   <li>{@code HttpRequestExecutor} — a verifier that must consult the platform vendor (Android's
 *       attestation status list, Play Integrity's online decode) needs the shared client rather
 *       than one of its own, so that SSRF protection and timeouts apply
 *   <li>{@code CacheStore} — registration is unauthenticated, so a verifier that fetches on every
 *       request lets an unauthenticated caller drive outbound traffic. Anything fetched has to be
 *       cacheable
 * </ul>
 *
 * <p>A verifier that needs something absent should have it added here explicitly, which is the
 * point: the addition is a decision rather than a side effect of what happened to be in scope.
 */
public class ClientInstanceRegistrationDependencyContainer {

  Map<Class<?>, Object> dependencies;

  public ClientInstanceRegistrationDependencyContainer() {
    this.dependencies = new HashMap<>();
  }

  public void register(Class<?> type, Object instance) {
    dependencies.put(type, instance);
  }

  /**
   * @throws ClientInstanceRegistrationDependencyMissingException when nothing is registered for
   *     {@code type}. This is a wiring error raised while the application assembles its verifiers,
   *     not a rejected request.
   */
  public <T> T resolve(Class<T> type) {
    if (!dependencies.containsKey(type)) {
      throw new ClientInstanceRegistrationDependencyMissingException(
          "Missing dependency for type: " + type.getName());
    }
    return type.cast(dependencies.get(type));
  }

  public boolean contains(Class<?> type) {
    return dependencies.containsKey(type);
  }
}
