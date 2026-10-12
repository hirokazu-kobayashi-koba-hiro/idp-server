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

package org.idp.server.core.extension.identity.verification.application.pre_hook.verification;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.idp.server.core.extension.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplication;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplicationIdentifier;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplicationProcessResult;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplicationProcessResults;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplicationStatus;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.platform.json.JsonConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** process_sequence: when a process may run, and what a rejection leaves behind (Issue #1971). */
class ProcessSequenceVerifierTest {

  private final JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
  private final ProcessSequenceVerifier verifier = new ProcessSequenceVerifier();
  private final IdentityVerificationProcess target = new IdentityVerificationProcess("target");

  private IdentityVerificationConfiguration configurationWith(Map<String, Object> dependencies) {
    Map<String, Object> payload =
        Map.of(
            "id",
            "0198e1c0-0000-7000-8000-00000000f971",
            "type",
            "probe",
            "processes",
            Map.of(
                "target",
                Map.of(
                    "dependencies",
                    dependencies,
                    "pre_hook",
                    Map.of("verifications", List.of(Map.of("type", "process_sequence"))))));
    return jsonConverter.read(payload, IdentityVerificationConfiguration.class);
  }

  private IdentityVerificationApplication application(
      IdentityVerificationApplicationStatus status,
      Map<String, IdentityVerificationApplicationProcessResult> processes) {
    return new IdentityVerificationApplication(
        new IdentityVerificationApplicationIdentifier("0198e1c0-0000-7000-8000-00000000a971"),
        new IdentityVerificationType("probe"),
        null,
        null,
        null,
        null,
        null,
        new IdentityVerificationApplicationProcessResults(processes),
        null,
        status,
        null);
  }

  private IdentityVerificationApplicationRequestVerifiedResult verify(
      IdentityVerificationApplication application, Map<String, Object> dependencies) {
    return verifier.verify(
        null,
        null,
        application,
        null,
        new IdentityVerificationType("probe"),
        target,
        null,
        null,
        null,
        configurationWith(dependencies));
  }

  @Test
  @DisplayName("申込みが無いときに allow_during_statuses があれば、ステータスを読まずに拒否する")
  void statusRestrictionWithoutApplicationIsRejected() {
    IdentityVerificationApplicationRequestVerifiedResult result =
        verify(
            new IdentityVerificationApplication(),
            Map.of("allow_during_statuses", List.of("requested")));

    assertTrue(result.isError());
    assertEquals(
        List.of(
            "Process 'target' cannot be executed before the application exists. Allowed statuses: requested"),
        result.errors());
  }

  @Test
  @DisplayName("申込みが無いときは、前提プロセスとステータスの両方を返す")
  void requiredProcessAndStatusWithoutApplication() {
    IdentityVerificationApplicationRequestVerifiedResult result =
        verify(
            new IdentityVerificationApplication(),
            Map.of(
                "required_processes",
                List.of("apply"),
                "allow_during_statuses",
                List.of("applying")));

    assertEquals(2, result.errors().size());
  }

  @Test
  @DisplayName("許可されたステータスなら通る")
  void allowedStatusPasses() {
    IdentityVerificationApplicationRequestVerifiedResult result =
        verify(
            application(IdentityVerificationApplicationStatus.APPLYING, Map.of()),
            Map.of("allow_during_statuses", List.of("requested", "applying")));

    assertTrue(result.isValid());
  }

  @Test
  @DisplayName("許可されていないステータスなら拒否する")
  void otherStatusIsRejected() {
    IdentityVerificationApplicationRequestVerifiedResult result =
        verify(
            application(IdentityVerificationApplicationStatus.APPROVED, Map.of()),
            Map.of("allow_during_statuses", List.of("requested", "applying")));

    assertTrue(result.isError());
  }

  @Test
  @DisplayName("process_sequence で拒否した呼び出しは、process の試行に数えない")
  void rejectionIsNotAnAttempt() {
    IdentityVerificationApplication applied =
        application(IdentityVerificationApplicationStatus.APPLYING, Map.of());

    IdentityVerificationApplicationRequestVerifiedResult missingRequired =
        verify(applied, Map.of("required_processes", List.of("crm-registration")));
    IdentityVerificationApplicationRequestVerifiedResult retry =
        verify(
            application(
                IdentityVerificationApplicationStatus.APPLYING,
                Map.of("target", new IdentityVerificationApplicationProcessResult(1, 0, 1))),
            Map.of());

    assertTrue(missingRequired.isError());
    assertFalse(missingRequired.isAttempt());
    assertTrue(retry.isError());
    assertFalse(retry.isAttempt());
  }

  @Test
  @DisplayName("ほかの検証の失敗は、これまでどおり試行に数える")
  void otherFailureIsAnAttempt() {
    IdentityVerificationApplicationRequestVerifiedResult failure =
        IdentityVerificationApplicationRequestVerifiedResult.failure(List.of("mismatch"));

    assertTrue(failure.isAttempt());
  }
}
