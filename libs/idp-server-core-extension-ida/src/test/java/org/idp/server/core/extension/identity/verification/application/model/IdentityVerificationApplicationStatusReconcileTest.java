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

package org.idp.server.core.extension.identity.verification.application.model;

import static org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplicationStatus.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the status reconciliation guard (#1617): terminal states are absorbing and
 * backward movement within the running phases is forbidden, so the stateless evaluator's fixed
 * fallback (APPLYING on process, EXAMINATION_PROCESSING on callback) cannot rewind progress or
 * overwrite a terminal state.
 */
class IdentityVerificationApplicationStatusReconcileTest {

  @Test
  void forwardWithinRunningIsAllowed() {
    assertEquals(
        APPLIED, IdentityVerificationApplicationStatusEvaluator.reconcile(APPLYING, APPLIED));
    assertEquals(
        EXAMINATION_PROCESSING,
        IdentityVerificationApplicationStatusEvaluator.reconcile(APPLIED, EXAMINATION_PROCESSING));
    // callback no-match fallback advancing from an earlier running phase is still forward progress
    assertEquals(
        EXAMINATION_PROCESSING,
        IdentityVerificationApplicationStatusEvaluator.reconcile(APPLYING, EXAMINATION_PROCESSING));
  }

  @Test
  void backwardWithinRunningIsHeld() {
    // process no-match fallback (APPLYING) after a callback already reached EXAMINATION_PROCESSING
    assertEquals(
        EXAMINATION_PROCESSING,
        IdentityVerificationApplicationStatusEvaluator.reconcile(EXAMINATION_PROCESSING, APPLYING));
    assertEquals(
        APPLIED, IdentityVerificationApplicationStatusEvaluator.reconcile(APPLIED, APPLYING));
    assertEquals(
        APPLYING, IdentityVerificationApplicationStatusEvaluator.reconcile(APPLYING, REQUESTED));
  }

  @Test
  void sameRunningPhaseIsKept() {
    assertEquals(
        APPLYING, IdentityVerificationApplicationStatusEvaluator.reconcile(APPLYING, APPLYING));
  }

  @Test
  void transitionToTerminalIsAllowed() {
    assertEquals(
        APPROVED, IdentityVerificationApplicationStatusEvaluator.reconcile(APPLYING, APPROVED));
    assertEquals(
        REJECTED,
        IdentityVerificationApplicationStatusEvaluator.reconcile(EXAMINATION_PROCESSING, REJECTED));
    assertEquals(
        CANCELLED, IdentityVerificationApplicationStatusEvaluator.reconcile(REQUESTED, CANCELLED));
  }

  @Test
  void terminalIsAbsorbing() {
    // once terminal, neither a running fallback nor another terminal moves it
    assertEquals(
        APPROVED, IdentityVerificationApplicationStatusEvaluator.reconcile(APPROVED, APPLYING));
    assertEquals(
        APPROVED, IdentityVerificationApplicationStatusEvaluator.reconcile(APPROVED, REJECTED));
    assertEquals(
        REJECTED,
        IdentityVerificationApplicationStatusEvaluator.reconcile(REJECTED, EXAMINATION_PROCESSING));
    assertEquals(
        EXPIRED, IdentityVerificationApplicationStatusEvaluator.reconcile(EXPIRED, APPROVED));
    assertEquals(
        CANCELLED, IdentityVerificationApplicationStatusEvaluator.reconcile(CANCELLED, APPLIED));
  }

  @Test
  void neutralCurrentAcceptsCandidate() {
    // current=UNKNOWN / UNDEFINED is neither terminal nor running, so neither guard applies and the
    // candidate is accepted as-is (recovery from a persisted-data anomaly). #1617 review follow-up.
    assertEquals(
        APPLYING, IdentityVerificationApplicationStatusEvaluator.reconcile(UNKNOWN, APPLYING));
    assertEquals(
        APPROVED, IdentityVerificationApplicationStatusEvaluator.reconcile(UNKNOWN, APPROVED));
    assertEquals(
        APPLIED, IdentityVerificationApplicationStatusEvaluator.reconcile(UNDEFINED, APPLIED));
  }

  @Test
  void enumTerminalSet() {
    assertTrue(APPROVED.isTerminal());
    assertTrue(REJECTED.isTerminal());
    assertTrue(EXPIRED.isTerminal());
    assertTrue(CANCELLED.isTerminal());
    assertFalse(REQUESTED.isTerminal());
    assertFalse(APPLYING.isTerminal());
    assertFalse(APPLIED.isTerminal());
    assertFalse(EXAMINATION_PROCESSING.isTerminal());
  }

  @Test
  void enumRunningRankOrder() {
    assertTrue(REQUESTED.runningRank() < APPLYING.runningRank());
    assertTrue(APPLYING.runningRank() < APPLIED.runningRank());
    assertTrue(APPLIED.runningRank() < EXAMINATION_PROCESSING.runningRank());
    // non-running states carry no running rank
    assertEquals(-1, APPROVED.runningRank());
    assertEquals(-1, UNKNOWN.runningRank());
  }
}
