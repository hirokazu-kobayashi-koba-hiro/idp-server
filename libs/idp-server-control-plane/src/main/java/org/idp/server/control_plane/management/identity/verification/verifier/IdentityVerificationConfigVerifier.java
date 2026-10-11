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

package org.idp.server.control_plane.management.identity.verification.verifier;

import java.util.List;
import org.idp.server.control_plane.management.exception.InvalidRequestException;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;

/**
 * Refuses an identity verification configuration the runtime would read differently from what it
 * says (Issue #1966). The rules live with the configuration ({@link
 * IdentityVerificationConfiguration#violations()}), next to the ones the runtime applies.
 */
public class IdentityVerificationConfigVerifier {

  public void verify(IdentityVerificationConfiguration configuration) {
    List<String> violations = configuration.violations();
    if (!violations.isEmpty()) {
      throw new InvalidRequestException(
          "identity verification configuration validation is failed", violations);
    }
  }
}
