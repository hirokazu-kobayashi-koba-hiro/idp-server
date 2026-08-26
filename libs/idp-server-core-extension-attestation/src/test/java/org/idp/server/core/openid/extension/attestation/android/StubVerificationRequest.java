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

package org.idp.server.core.openid.extension.attestation.android;

import java.time.LocalDateTime;
import java.util.Map;
import org.idp.server.core.openid.clientinstance.registration.ClientInstanceRegistrationChallenge;
import org.idp.server.core.openid.clientinstance.registration.PlatformAttestationVerificationRequest;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

/** Assembles a verification request the way the registration service would. */
class StubVerificationRequest {

  static final String TENANT_ID = "1e68932e-ed4a-43e7-b412-460665e42df3";
  static final String CLIENT_ID = "wallet-client";

  private static final JsonConverter JSON = JsonConverter.snakeCaseInstance();

  static PlatformAttestationVerificationRequest of(
      Map<String, Object> clientExtension,
      String challenge,
      Map<String, Object> instanceKey,
      Map<String, Object> evidence) {

    ClientConfiguration clientConfiguration =
        JSON.read(
            JSON.write(
                Map.of(
                    "client_id",
                    CLIENT_ID,
                    "token_endpoint_auth_method",
                    "attest_jwt_client_auth",
                    "extension",
                    clientExtension)),
            ClientConfiguration.class);

    ClientInstanceRegistrationChallenge registrationChallenge =
        new ClientInstanceRegistrationChallenge(
            challenge,
            TENANT_ID,
            CLIENT_ID,
            null,
            "instance-0001",
            LocalDateTime.now().plusMinutes(5),
            null,
            LocalDateTime.now());

    return new PlatformAttestationVerificationRequest(
        tenant(), clientConfiguration, registrationChallenge, instanceKey, evidence);
  }

  private static Tenant tenant() {
    return new Tenant(
        new TenantIdentifier(TENANT_ID),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        true);
  }
}
