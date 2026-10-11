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

import static org.idp.server.core.extension.identity.verification.configuration.process.IdentityVerificationProcessCaller.end_user;
import static org.idp.server.core.extension.identity.verification.configuration.process.IdentityVerificationProcessCaller.external_service;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.extension.identity.exception.IdentityVerificationApplicationConfigurationNotFoundException;
import org.idp.server.core.extension.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.extension.identity.verification.configuration.process.IdentityVerificationProcessConfiguration;
import org.idp.server.platform.json.JsonConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Which endpoint may run a process (Issue #1966).
 *
 * <p>The end-user endpoint and the callback endpoint look processes up in the same map, so without
 * a caller a process meant for the external service could also be run from the end-user endpoint.
 */
class IdentityVerificationProcessCallerTest {

  private final JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  private IdentityVerificationConfiguration configurationWith(Map<String, Object> process) {
    Map<String, Object> payload =
        Map.of(
            "id",
            "0198e1c0-0000-7000-8000-00000000f966",
            "type",
            "probe",
            "processes",
            Map.of("probe-process", process));
    return jsonConverter.read(payload, IdentityVerificationConfiguration.class);
  }

  private IdentityVerificationProcessConfiguration processWith(Map<String, Object> process) {
    return configurationWith(process)
        .getProcessConfig(new IdentityVerificationProcess("probe-process"));
  }

  private Map<String, Object> withBasicAuth(Map<String, Object> process) {
    Map<String, Object> withAuth = new HashMap<>(process);
    withAuth.put(
        "request", Map.of("basic_auth", Map.of("username", "vendor", "password", "secret")));
    return withAuth;
  }

  @Test
  @DisplayName("caller が end_user なら、エンドユーザー向けの入口からだけ実行できる")
  void endUserProcessRunsOnlyFromEndUserEndpoint() {
    IdentityVerificationProcessConfiguration process = processWith(Map.of("caller", "end_user"));

    assertTrue(process.isCallableBy(end_user));
    assertFalse(process.isCallableBy(external_service));
  }

  @Test
  @DisplayName("caller が external_service なら、コールバックからだけ実行できる")
  void externalServiceProcessRunsOnlyFromCallback() {
    IdentityVerificationProcessConfiguration process =
        processWith(Map.of("caller", "external_service"));

    assertFalse(process.isCallableBy(end_user));
    assertTrue(process.isCallableBy(external_service));
  }

  @Test
  @DisplayName("caller が無く basic_auth があれば、コールバックからだけ実行できる")
  void basicAuthWithoutCallerIsTreatedAsExternalService() {
    IdentityVerificationProcessConfiguration process = processWith(withBasicAuth(Map.of()));

    assertFalse(process.isCallableBy(end_user));
    assertTrue(process.isCallableBy(external_service));
  }

  @Test
  @DisplayName("caller を明示すれば、basic_auth があってもその入口に従う")
  void explicitCallerWinsOverBasicAuth() {
    IdentityVerificationProcessConfiguration process =
        processWith(withBasicAuth(Map.of("caller", "end_user")));

    assertTrue(process.isCallableBy(end_user));
    assertFalse(process.isCallableBy(external_service));
  }

  @Test
  @DisplayName("caller も basic_auth も無ければ、これまでどおり両方から実行できる")
  void unmarkedProcessRunsFromBothEndpoints() {
    IdentityVerificationProcessConfiguration process = processWith(Map.of());

    assertTrue(process.isCallableBy(end_user));
    assertTrue(process.isCallableBy(external_service));
  }

  @Test
  @DisplayName("caller が知らない値なら、どちらからも実行できない")
  void unknownCallerRunsFromNeither() {
    IdentityVerificationProcessConfiguration process = processWith(Map.of("caller", "endUser"));

    assertFalse(process.isCallableBy(end_user));
    assertFalse(process.isCallableBy(external_service));
  }

  @Test
  @DisplayName("入口から実行できない process は、登録されていない process と同じく見つからない扱いにする")
  void processNotCallableFromEntryIsReportedAsUnregistered() {
    IdentityVerificationConfiguration configuration =
        configurationWith(Map.of("caller", "external_service"));
    IdentityVerificationProcess process = new IdentityVerificationProcess("probe-process");

    IdentityVerificationApplicationConfigurationNotFoundException notCallable =
        assertThrows(
            IdentityVerificationApplicationConfigurationNotFoundException.class,
            () -> configuration.getProcessConfig(process, end_user));
    IdentityVerificationApplicationConfigurationNotFoundException unregistered =
        assertThrows(
            IdentityVerificationApplicationConfigurationNotFoundException.class,
            () -> configuration.getProcessConfig(new IdentityVerificationProcess("missing")));

    assertEquals(
        unregistered.getMessage().replace("missing", "probe-process"), notCallable.getMessage());
    assertNotNull(configuration.getProcessConfig(process, external_service));
  }

  @Test
  @DisplayName("caller は管理 API の応答に返る")
  void callerSurvivesRoundTrip() {
    Map<String, Object> roundTripped = processWith(Map.of("caller", "external_service")).toMap();

    assertEquals("external_service", roundTripped.get("caller"));
  }
}
