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

package org.idp.server.authenticators.webauthn4j.mds;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuthenticatorStatusTest {

  private static final String TEST_AAGUID = "ee882879-721c-4913-9775-3dfcce97072a";

  @Test
  void notFound_shouldReturnCorrectValues() {
    AuthenticatorStatus status = AuthenticatorStatus.notFound(TEST_AAGUID);

    assertEquals(TEST_AAGUID, status.aaguid());
    assertFalse(status.isFound());
    assertFalse(status.isCompromised());
    assertFalse(status.isFidoCertified());
    assertEquals("NOT_FOUND", status.latestStatus());
    assertNull(status.effectiveDate());
    assertTrue(status.statusHistory().isEmpty());
    assertFalse(status.isTrusted());
  }

  @Test
  void isCompromised_shouldReturnTrue_forAttestationKeyCompromise() {
    AuthenticatorStatus status =
        AuthenticatorStatus.of(
            TEST_AAGUID,
            com.webauthn4j.metadata.data.toc.AuthenticatorStatus.ATTESTATION_KEY_COMPROMISE,
            LocalDate.now(),
            List.of("ATTESTATION_KEY_COMPROMISE"));

    assertTrue(status.isCompromised());
    assertFalse(status.isTrusted());
  }

  @Test
  void isCompromised_shouldReturnTrue_forUserVerificationBypass() {
    AuthenticatorStatus status =
        AuthenticatorStatus.of(
            TEST_AAGUID,
            com.webauthn4j.metadata.data.toc.AuthenticatorStatus.USER_VERIFICATION_BYPASS,
            LocalDate.now(),
            List.of("USER_VERIFICATION_BYPASS"));

    assertTrue(status.isCompromised());
  }

  @Test
  void isCompromised_shouldReturnTrue_forRevoked() {
    AuthenticatorStatus status =
        AuthenticatorStatus.of(
            TEST_AAGUID,
            com.webauthn4j.metadata.data.toc.AuthenticatorStatus.REVOKED,
            LocalDate.now(),
            List.of("REVOKED"));

    assertTrue(status.isCompromised());
  }

  @Test
  void isCompromised_shouldReturnTrue_forUserKeyRemoteCompromise() {
    AuthenticatorStatus status =
        AuthenticatorStatus.of(
            TEST_AAGUID,
            com.webauthn4j.metadata.data.toc.AuthenticatorStatus.USER_KEY_REMOTE_COMPROMISE,
            LocalDate.now(),
            List.of("USER_KEY_REMOTE_COMPROMISE"));

    assertTrue(status.isCompromised());
  }

  @Test
  void isCompromised_shouldReturnTrue_forUserKeyPhysicalCompromise() {
    AuthenticatorStatus status =
        AuthenticatorStatus.of(
            TEST_AAGUID,
            com.webauthn4j.metadata.data.toc.AuthenticatorStatus.USER_KEY_PHYSICAL_COMPROMISE,
            LocalDate.now(),
            List.of("USER_KEY_PHYSICAL_COMPROMISE"));

    assertTrue(status.isCompromised());
  }

  @Test
  void isCompromised_shouldReturnFalse_forFidoCertified() {
    AuthenticatorStatus status =
        AuthenticatorStatus.of(
            TEST_AAGUID,
            com.webauthn4j.metadata.data.toc.AuthenticatorStatus.FIDO_CERTIFIED,
            LocalDate.now(),
            List.of("FIDO_CERTIFIED"));

    assertFalse(status.isCompromised());
  }

  @Test
  void isCompromised_shouldReturnFalse_forNull() {
    AuthenticatorStatus status =
        AuthenticatorStatus.of(TEST_AAGUID, null, LocalDate.now(), List.of());

    assertFalse(status.isCompromised());
  }

  @Test
  void isFidoCertified_shouldReturnTrue_forFidoCertified() {
    AuthenticatorStatus status =
        AuthenticatorStatus.of(
            TEST_AAGUID,
            com.webauthn4j.metadata.data.toc.AuthenticatorStatus.FIDO_CERTIFIED,
            LocalDate.now(),
            List.of("FIDO_CERTIFIED"));

    assertTrue(status.isFidoCertified());
  }

  @Test
  void isFidoCertified_shouldReturnTrue_forFidoCertifiedL1() {
    AuthenticatorStatus status =
        AuthenticatorStatus.of(
            TEST_AAGUID,
            com.webauthn4j.metadata.data.toc.AuthenticatorStatus.FIDO_CERTIFIED_L1,
            LocalDate.now(),
            List.of("FIDO_CERTIFIED_L1"));

    assertTrue(status.isFidoCertified());
  }

  @Test
  void isFidoCertified_shouldReturnTrue_forFidoCertifiedL2() {
    AuthenticatorStatus status =
        AuthenticatorStatus.of(
            TEST_AAGUID,
            com.webauthn4j.metadata.data.toc.AuthenticatorStatus.FIDO_CERTIFIED_L2,
            LocalDate.now(),
            List.of("FIDO_CERTIFIED_L2"));

    assertTrue(status.isFidoCertified());
  }

  @Test
  void isFidoCertified_shouldReturnTrue_forFidoCertifiedL3() {
    AuthenticatorStatus status =
        AuthenticatorStatus.of(
            TEST_AAGUID,
            com.webauthn4j.metadata.data.toc.AuthenticatorStatus.FIDO_CERTIFIED_L3,
            LocalDate.now(),
            List.of("FIDO_CERTIFIED_L3"));

    assertTrue(status.isFidoCertified());
  }

  @Test
  void isFidoCertified_shouldReturnFalse_forNotFidoCertified() {
    AuthenticatorStatus status =
        AuthenticatorStatus.of(
            TEST_AAGUID,
            com.webauthn4j.metadata.data.toc.AuthenticatorStatus.NOT_FIDO_CERTIFIED,
            LocalDate.now(),
            List.of("NOT_FIDO_CERTIFIED"));

    assertFalse(status.isFidoCertified());
  }

  @Test
  void isFidoCertified_shouldReturnFalse_forNull() {
    AuthenticatorStatus status =
        AuthenticatorStatus.of(TEST_AAGUID, null, LocalDate.now(), List.of());

    assertFalse(status.isFidoCertified());
  }

  @Test
  void isTrusted_shouldReturnTrue_whenFoundAndNotCompromised() {
    AuthenticatorStatus status =
        AuthenticatorStatus.of(
            TEST_AAGUID,
            com.webauthn4j.metadata.data.toc.AuthenticatorStatus.FIDO_CERTIFIED,
            LocalDate.now(),
            List.of("FIDO_CERTIFIED"));

    assertTrue(status.isTrusted());
    assertTrue(status.isFound());
    assertFalse(status.isCompromised());
  }

  @Test
  void isTrusted_shouldReturnFalse_whenNotFound() {
    AuthenticatorStatus status = AuthenticatorStatus.notFound(TEST_AAGUID);

    assertFalse(status.isTrusted());
  }

  @Test
  void isTrusted_shouldReturnFalse_whenCompromised() {
    AuthenticatorStatus status =
        AuthenticatorStatus.of(
            TEST_AAGUID,
            com.webauthn4j.metadata.data.toc.AuthenticatorStatus.REVOKED,
            LocalDate.now(),
            List.of("REVOKED"));

    assertFalse(status.isTrusted());
    assertTrue(status.isFound());
    assertTrue(status.isCompromised());
  }

  @Test
  void of_shouldStoreEffectiveDateAndStatusHistory() {
    LocalDate effectiveDate = LocalDate.of(2025, 1, 15);
    List<String> statusHistory =
        List.of("NOT_FIDO_CERTIFIED", "FIDO_CERTIFIED", "FIDO_CERTIFIED_L1");

    AuthenticatorStatus status =
        AuthenticatorStatus.of(
            TEST_AAGUID,
            com.webauthn4j.metadata.data.toc.AuthenticatorStatus.FIDO_CERTIFIED_L1,
            effectiveDate,
            statusHistory);

    assertEquals(effectiveDate, status.effectiveDate());
    assertEquals(statusHistory, status.statusHistory());
    assertEquals("FIDO_CERTIFIED_L1", status.latestStatus());
  }

  @Test
  void of_shouldHandleNullStatus() {
    AuthenticatorStatus status =
        AuthenticatorStatus.of(TEST_AAGUID, null, LocalDate.now(), List.of());

    assertEquals("UNKNOWN", status.latestStatus());
    assertTrue(status.isFound());
  }
}
