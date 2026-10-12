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

package org.idp.server.core.extension.identity.verification.configuration;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.idp.server.platform.json.JsonConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** allow_during_statuses is compared with the status as the API returns it (Issue #1971). */
class ProcessDependenciesViolationTest {

  private final JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  private List<String> violationsOf(List<String> allowDuringStatuses) {
    Map<String, Object> payload =
        Map.of(
            "id",
            "0198e1c0-0000-7000-8000-00000000f972",
            "type",
            "probe",
            "processes",
            Map.of(
                "cancel",
                Map.of("dependencies", Map.of("allow_during_statuses", allowDuringStatuses))));
    return jsonConverter.read(payload, IdentityVerificationConfiguration.class).violations();
  }

  @Test
  @DisplayName("申込みのステータスの値なら違反なし")
  void knownStatusesPass() {
    assertEquals(
        List.of(), violationsOf(List.of("requested", "applying", "examination_processing")));
  }

  @Test
  @DisplayName("大文字や知らない値は違反")
  void unknownStatusesAreViolations() {
    List<String> violations = violationsOf(List.of("REQUESTED", "unknown", ""));

    assertEquals(3, violations.size());
    assertTrue(violations.get(0).contains("dependencies.allow_during_statuses"));
    assertTrue(violations.get(0).contains("REQUESTED"));
  }
}
