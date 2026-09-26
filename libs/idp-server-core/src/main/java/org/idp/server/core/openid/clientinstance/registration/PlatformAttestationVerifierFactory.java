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

/**
 * Builds a {@link PlatformAttestationVerifier}, so that a verifier can hold collaborators.
 *
 * <p>This is the type registered in {@code META-INF/services}, not the verifier itself. {@code
 * ServiceLoader} can only call a no-argument constructor, which bounded what a verifier could
 * check: everything had to be derivable from the evidence in hand. The checks that are still
 * missing are the ones that are not — an Android attestation certificate cannot be known to be
 * unrevoked without asking Google, and Play Integrity is decoded online by definition.
 *
 * <p>Introducing the factory before those land keeps the change out of the published extension
 * point. A verifier in a {@code plugins/} jar implements this interface, so changing its shape
 * afterwards would break implementations this project does not see.
 *
 * <p>Mirrors {@code AuthenticationExecutorFactory}, including the narrowed container: see {@link
 * ClientInstanceRegistrationDependencyContainer} for why the application's own container is not
 * what is handed over.
 */
public interface PlatformAttestationVerifierFactory {

  PlatformAttestationVerifier create(ClientInstanceRegistrationDependencyContainer container);
}
