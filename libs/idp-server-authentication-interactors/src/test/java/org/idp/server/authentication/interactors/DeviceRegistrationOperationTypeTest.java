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

package org.idp.server.authentication.interactors;

import static org.junit.jupiter.api.Assertions.*;

import org.idp.server.authentication.interactors.fido2.Fido2DeregistrationInteractor;
import org.idp.server.authentication.interactors.fido2.Fido2RegistrationInteractor;
import org.idp.server.authentication.interactors.fidouaf.FidoUafDeRegistrationInteractor;
import org.idp.server.authentication.interactors.fidouaf.FidoUafRegistrationInteractor;
import org.idp.server.core.openid.authentication.OperationType;
import org.junit.jupiter.api.Test;

/**
 * Registering or removing a device proves nothing about who the user is, so it must not count as an
 * authentication (amr, auth_time, {@code AuthenticationTransaction#hasTrustedUser}).
 */
class DeviceRegistrationOperationTypeTest {

  @Test
  void fido2RegistrationIsARegistration() {
    assertEquals(
        OperationType.REGISTRATION, new Fido2RegistrationInteractor(null, null).operationType());
  }

  @Test
  void fidoUafRegistrationIsARegistration() {
    assertEquals(
        OperationType.REGISTRATION,
        new FidoUafRegistrationInteractor(null, null, null, null).operationType());
  }

  @Test
  void fido2DeregistrationIsADeregistration() {
    assertEquals(
        OperationType.DE_REGISTRATION,
        new Fido2DeregistrationInteractor(null, null).operationType());
  }

  @Test
  void fidoUafDeregistrationIsADeregistration() {
    assertEquals(
        OperationType.DE_REGISTRATION,
        new FidoUafDeRegistrationInteractor(null, null, null).operationType());
  }
}
