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

import com.webauthn4j.data.attestation.authenticator.AAGUID;
import com.webauthn4j.metadata.data.statement.MetadataStatement;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NoOperationMdsResolverTest {

  private NoOperationMdsResolver resolver;

  @BeforeEach
  void setUp() {
    resolver = new NoOperationMdsResolver();
  }

  @Test
  void resolve_withAaguid_shouldReturnEmpty() {
    AAGUID aaguid = new AAGUID(UUID.randomUUID());

    Optional<MetadataStatement> result = resolver.resolve(aaguid);

    assertTrue(result.isEmpty());
  }

  @Test
  void resolve_withString_shouldReturnEmpty() {
    Optional<MetadataStatement> result = resolver.resolve("ee882879-721c-4913-9775-3dfcce97072a");

    assertTrue(result.isEmpty());
  }

  @Test
  void resolve_withNull_shouldReturnEmpty() {
    Optional<MetadataStatement> result = resolver.resolve((AAGUID) null);

    assertTrue(result.isEmpty());
  }

  @Test
  void checkStatus_withAaguid_shouldReturnNotFound() {
    AAGUID aaguid = new AAGUID(UUID.randomUUID());

    AuthenticatorStatus status = resolver.checkStatus(aaguid);

    assertFalse(status.isFound());
    assertEquals(aaguid.toString(), status.aaguid());
  }

  @Test
  void checkStatus_withString_shouldReturnNotFound() {
    String aaguid = "ee882879-721c-4913-9775-3dfcce97072a";

    AuthenticatorStatus status = resolver.checkStatus(aaguid);

    assertFalse(status.isFound());
    assertEquals(aaguid, status.aaguid());
  }

  @Test
  void checkStatus_withNull_shouldReturnUnknown() {
    AuthenticatorStatus status = resolver.checkStatus((AAGUID) null);

    assertFalse(status.isFound());
    assertEquals("unknown", status.aaguid());
  }

  @Test
  void isCompromised_shouldAlwaysReturnFalse() {
    AAGUID aaguid = new AAGUID(UUID.randomUUID());

    assertFalse(resolver.isCompromised(aaguid));
    assertFalse(resolver.isCompromised("ee882879-721c-4913-9775-3dfcce97072a"));
    assertFalse(resolver.isCompromised((AAGUID) null));
    assertFalse(resolver.isCompromised((String) null));
  }

  @Test
  void refresh_shouldNotThrowException() {
    assertDoesNotThrow(() -> resolver.refresh());
  }
}
