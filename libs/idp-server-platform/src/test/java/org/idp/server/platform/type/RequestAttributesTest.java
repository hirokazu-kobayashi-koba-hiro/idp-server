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

package org.idp.server.platform.type;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pins the {@link RequestAttributes#toMap()} contract that mapping contexts depend on.
 *
 * <p>{@code RequestAttributes} holds a single {@link org.idp.server.platform.json.JsonNodeWrapper}
 * field, so handing the object itself to a mapping context serializes it as {@code
 * {"json_node_wrapper":{"json_node":{...}}}} and {@code $.request_attributes.ip_address} resolves
 * to null. Callers must go through {@code toMap()}; these tests fix both what it returns and that
 * it tolerates the value-less instance (#1773).
 */
class RequestAttributesTest {

  private static Map<String, Object> inboundAttributes() {
    Map<String, Object> headers = new LinkedHashMap<>();
    headers.put("User-Agent", "axios/1.3.4");
    headers.put("X-Forwarded-For", "203.0.113.7");

    Map<String, Object> values = new LinkedHashMap<>();
    values.put("ip_address", "172.20.0.6");
    values.put("user_agent", "axios/1.3.4");
    values.put("resource", "/{tenant-id}/v1/authorizations/{id}/external-api-authentication");
    values.put("action", "POST");
    values.put("headers", headers);
    return values;
  }

  @Nested
  class ToMap {

    @Test
    void returnsTheAttributesThemselvesNotTheWrapperStructure() {
      Map<String, Object> result = new RequestAttributes(inboundAttributes()).toMap();

      assertEquals("172.20.0.6", result.get("ip_address"));
      assertEquals("axios/1.3.4", result.get("user_agent"));
      assertEquals("POST", result.get("action"));
      // The shape that leaked into mapping contexts before #1773.
      assertFalse(result.containsKey("json_node_wrapper"));
    }

    @Test
    void nestedHeadersStayNavigable() {
      Map<String, Object> result = new RequestAttributes(inboundAttributes()).toMap();

      Object headers = result.get("headers");
      assertInstanceOf(Map.class, headers);
      assertEquals("axios/1.3.4", ((Map<?, ?>) headers).get("User-Agent"));
    }

    @Test
    void valuelessInstanceReturnsEmptyMapInsteadOfThrowing() {
      // FidoUafUserDataDeletionExecutor passes new RequestAttributes() on the user-lifecycle path,
      // where there is no inbound HTTP request. toMap() has to absorb that.
      assertEquals(Map.of(), new RequestAttributes().toMap());
    }

    @Test
    void emptyAttributesReturnEmptyMap() {
      assertEquals(Map.of(), new RequestAttributes(Map.of()).toMap());
    }
  }

  @Nested
  class Exists {

    @Test
    void valuelessInstanceDoesNotExist() {
      assertFalse(new RequestAttributes().exists());
    }

    @Test
    void populatedInstanceExists() {
      assertTrue(new RequestAttributes(inboundAttributes()).exists());
    }
  }
}
