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

package org.idp.server.control_plane.management.oidc.authorization.verifier;

import java.util.List;
import org.idp.server.control_plane.management.exception.InvalidRequestException;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;

/**
 * Refuses an authorization server configuration whose values would break the tenant at runtime
 * (Issue #1893): an iat window out of range would refuse every DPoP proof or Client Attestation PoP
 * JWT, or hardly limit their reuse.
 *
 * <p>Applied wherever a configuration comes in: tenant creation, onboarding, the starter, and
 * updates.
 */
public class AuthorizationServerConfigurationVerifier {

  public void verify(AuthorizationServerConfiguration configuration) {
    List<String> violations = configuration.acceptableWindowViolations();
    if (!violations.isEmpty()) {
      throw new InvalidRequestException(String.join("; ", violations));
    }
  }
}
