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

package org.idp.server.core.openid.clientinstance.registration.verifier;

import org.idp.server.core.openid.clientinstance.ClientInstanceRegistrationPolicy;
import org.idp.server.core.openid.clientinstance.registration.ClientInstanceRegistrationException;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Decides what a registration ticket may be issued for.
 *
 * <p>Every authorization decision of the flow is made here, at issuance: the registration endpoint
 * then only has to check that the ticket is valid and that the evidence is bound to it.
 */
public class ClientInstanceRegistrationPolicyVerifier {

  static final String IDP_SERVER_PROVIDER_ID = "idp-server";

  UserQueryRepository userQueryRepository;

  public ClientInstanceRegistrationPolicyVerifier(UserQueryRepository userQueryRepository) {
    this.userQueryRepository = userQueryRepository;
  }

  public void verify(
      Tenant tenant,
      ClientConfiguration clientConfiguration,
      RequestedClientId requestedClientId,
      String deviceId) {

    throwExceptionIfClientDoesNotUseAttestation(clientConfiguration, requestedClientId);
    throwExceptionIfDeviceIsNotAcceptable(tenant, clientConfiguration, deviceId);
  }

  private void throwExceptionIfClientDoesNotUseAttestation(
      ClientConfiguration clientConfiguration, RequestedClientId requestedClientId) {
    if (!clientConfiguration.clientAuthenticationType().isAttestJwtClientAuth()) {
      throw new ClientInstanceRegistrationException(
          "client does not use attest_jwt_client_auth: " + requestedClientId.value());
    }
  }

  /**
   * Platform attestation proves that a genuine application runs on a genuine device, but it carries
   * no device identifier: nothing in the evidence ties it to the device_id of the request. When the
   * client requires an authentication device, the device_id is therefore checked against the
   * devices this server issued, so that an arbitrary value cannot be baked into the ticket.
   */
  private void throwExceptionIfDeviceIsNotAcceptable(
      Tenant tenant, ClientConfiguration clientConfiguration, String deviceId) {

    ClientInstanceRegistrationPolicy policy =
        clientConfiguration.clientInstanceRegistrationPolicy();

    if (policy.isUndefined()) {
      throw new ClientInstanceRegistrationException(
          "client_instance_registration_policy is not configured or has an unknown value");
    }

    if (!policy.requiresAuthenticationDevice()) {
      return;
    }

    if (deviceId == null) {
      throw new ClientInstanceRegistrationException(
          "device_id is required by client_instance_registration_policy");
    }

    User user =
        userQueryRepository.findByDeviceId(
            tenant, new AuthenticationDeviceIdentifier(deviceId), IDP_SERVER_PROVIDER_ID);

    if (!user.exists()) {
      throw new ClientInstanceRegistrationException(
          "device_id is not a registered authentication device: " + deviceId);
    }
  }
}
