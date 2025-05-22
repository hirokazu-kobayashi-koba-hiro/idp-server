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


package org.idp.server.control_plane.base.verifier;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class ClientVerifier {

  ClientConfigurationQueryRepository clientConfigurationQueryRepository;

  public ClientVerifier(ClientConfigurationQueryRepository clientConfigurationQueryRepository) {
    this.clientConfigurationQueryRepository = clientConfigurationQueryRepository;
  }

  public VerificationResult verify(Tenant tenant, ClientConfiguration clientConfiguration) {
    List<String> errors = new ArrayList<>();

    ClientConfiguration existing =
        clientConfigurationQueryRepository.find(tenant, clientConfiguration.clientIdentifier());
    if (existing.exists()) {
      errors.add("Client already exists");
    }

    if (!errors.isEmpty()) {
      return VerificationResult.failure(errors);
    }

    return VerificationResult.success();
  }
}
