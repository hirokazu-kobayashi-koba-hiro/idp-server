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

package org.idp.server.platform.http;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class HttpQueryParamsTest {

  @Test
  void add_urlEncodesValue() {
    HttpQueryParams params = new HttpQueryParams();
    params.add("query", "hello world");
    assertEquals("query=hello+world", params.params());
  }

  @Test
  void add_urlEncodesSpecialCharacters() {
    HttpQueryParams params = new HttpQueryParams();
    params.add("url", "https://example.com?foo=bar");
    assertEquals("url=https%3A%2F%2Fexample.com%3Ffoo%3Dbar", params.params());
  }

  @Test
  void add_urlEncodesKey() {
    HttpQueryParams params = new HttpQueryParams();
    params.add("key with space", "value");
    assertEquals("key+with+space=value", params.params());
  }

  @Test
  void constructor_urlEncodesValues() {
    HttpQueryParams params = new HttpQueryParams(Map.of("query", "hello world"));
    assertEquals("query=hello+world", params.params());
  }

  @Test
  void fromMapObject_urlEncodesValues() {
    HttpQueryParams params = HttpQueryParams.fromMapObject(Map.of("query", "hello world"));
    assertEquals("query=hello+world", params.params());
  }

  @Test
  void params_handlesMaliciousInput() {
    HttpQueryParams params = new HttpQueryParams();
    params.add("redirect", "http://evil.com&admin=true");
    String result = params.params();
    // Should be properly encoded, not allowing parameter injection
    assertTrue(result.contains("http%3A%2F%2Fevil.com%26admin%3Dtrue"));
  }

  @Test
  void params_handlesEmptyValue() {
    HttpQueryParams params = new HttpQueryParams();
    params.add("key", "");
    // Empty values should be skipped
    assertEquals("", params.params());
  }

  @Test
  void params_handlesMultipleParameters() {
    HttpQueryParams params = new HttpQueryParams();
    params.add("key1", "value1");
    params.add("key2", "value2");
    String result = params.params();
    assertTrue(result.contains("key1=value1"));
    assertTrue(result.contains("key2=value2"));
    assertTrue(result.contains("&"));
  }
}
