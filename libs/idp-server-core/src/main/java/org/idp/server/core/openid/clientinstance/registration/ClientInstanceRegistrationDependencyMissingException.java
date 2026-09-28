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
 * A verifier asked for a dependency the application did not register.
 *
 * <p>Separate from {@link ClientInstanceRegistrationException}, which reports a rejected
 * registration request. This one is raised while the application assembles its verifiers, so it
 * surfaces at startup and never as a response.
 */
public class ClientInstanceRegistrationDependencyMissingException extends RuntimeException {

  public ClientInstanceRegistrationDependencyMissingException(String message) {
    super(message);
  }
}
