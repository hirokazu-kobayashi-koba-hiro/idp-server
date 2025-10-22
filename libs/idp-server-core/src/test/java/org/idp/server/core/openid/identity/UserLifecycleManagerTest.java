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

package org.idp.server.core.openid.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.idp.server.platform.exception.UnSupportedException;
import org.junit.jupiter.api.Test;

class UserLifecycleManagerTest {

  @Test
  void testFederatedCanTransitToRegistered() {
    assertTrue(UserLifecycleManager.canTransit(UserStatus.FEDERATED, UserStatus.REGISTERED));
    assertEquals(
        UserStatus.REGISTERED,
        UserLifecycleManager.transit(UserStatus.FEDERATED, UserStatus.REGISTERED));
  }

  @Test
  void testFederatedCanTransitToIdentityVerified() {
    assertTrue(UserLifecycleManager.canTransit(UserStatus.FEDERATED, UserStatus.IDENTITY_VERIFIED));
    assertEquals(
        UserStatus.IDENTITY_VERIFIED,
        UserLifecycleManager.transit(UserStatus.FEDERATED, UserStatus.IDENTITY_VERIFIED));
  }

  @Test
  void testFederatedCanTransitToIdentityVerificationRequired() {
    assertTrue(
        UserLifecycleManager.canTransit(
            UserStatus.FEDERATED, UserStatus.IDENTITY_VERIFICATION_REQUIRED));
    assertEquals(
        UserStatus.IDENTITY_VERIFICATION_REQUIRED,
        UserLifecycleManager.transit(
            UserStatus.FEDERATED, UserStatus.IDENTITY_VERIFICATION_REQUIRED));
  }

  @Test
  void testFederatedCanTransitToLocked() {
    assertTrue(UserLifecycleManager.canTransit(UserStatus.FEDERATED, UserStatus.LOCKED));
    assertEquals(
        UserStatus.LOCKED, UserLifecycleManager.transit(UserStatus.FEDERATED, UserStatus.LOCKED));
  }

  @Test
  void testFederatedCanTransitToDisabled() {
    assertTrue(UserLifecycleManager.canTransit(UserStatus.FEDERATED, UserStatus.DISABLED));
    assertEquals(
        UserStatus.DISABLED,
        UserLifecycleManager.transit(UserStatus.FEDERATED, UserStatus.DISABLED));
  }

  @Test
  void testFederatedCanTransitToSuspended() {
    assertTrue(UserLifecycleManager.canTransit(UserStatus.FEDERATED, UserStatus.SUSPENDED));
    assertEquals(
        UserStatus.SUSPENDED,
        UserLifecycleManager.transit(UserStatus.FEDERATED, UserStatus.SUSPENDED));
  }

  @Test
  void testFederatedCannotTransitToDeleted() {
    assertFalse(UserLifecycleManager.canTransit(UserStatus.FEDERATED, UserStatus.DELETED));
    assertThrows(
        UnSupportedException.class,
        () -> UserLifecycleManager.transit(UserStatus.FEDERATED, UserStatus.DELETED));
  }

  @Test
  void testFederatedCannotTransitToDeletedPending() {
    assertFalse(UserLifecycleManager.canTransit(UserStatus.FEDERATED, UserStatus.DELETED_PENDING));
    assertThrows(
        UnSupportedException.class,
        () -> UserLifecycleManager.transit(UserStatus.FEDERATED, UserStatus.DELETED_PENDING));
  }

  @Test
  void testFederatedCannotTransitToDeactivated() {
    assertFalse(UserLifecycleManager.canTransit(UserStatus.FEDERATED, UserStatus.DEACTIVATED));
    assertThrows(
        UnSupportedException.class,
        () -> UserLifecycleManager.transit(UserStatus.FEDERATED, UserStatus.DEACTIVATED));
  }

  @Test
  void testExistingTransitionsStillWork() {
    assertTrue(UserLifecycleManager.canTransit(UserStatus.INITIALIZED, UserStatus.REGISTERED));
    assertTrue(
        UserLifecycleManager.canTransit(UserStatus.REGISTERED, UserStatus.IDENTITY_VERIFIED));
    assertTrue(UserLifecycleManager.canTransit(UserStatus.LOCKED, UserStatus.IDENTITY_VERIFIED));
  }
}
