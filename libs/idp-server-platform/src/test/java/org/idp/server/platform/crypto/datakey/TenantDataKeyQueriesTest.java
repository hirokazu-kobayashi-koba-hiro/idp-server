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
package org.idp.server.platform.crypto.datakey;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TenantDataKeyQueriesTest {

  @Test
  void normalizesTheStatusToHowItIsStored() {
    TenantDataKeyQueries queries = new TenantDataKeyQueries(Map.of("status", "ACTIVE"));
    assertTrue(queries.isStatusValid());
    assertEquals("active", queries.status());
  }

  @Test
  void leavesAnUnknownStatusForTheCallerToRefuse() {
    TenantDataKeyQueries queries = new TenantDataKeyQueries(Map.of("status", "retired"));
    assertFalse(queries.isStatusValid());
    assertEquals("retired", queries.status());
  }
}
