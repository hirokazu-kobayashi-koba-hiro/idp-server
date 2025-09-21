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

package org.idp.server.platform.mapper.functions;

import java.util.Map;
import java.util.UUID;

/**
 * {@code Uuid4Function} generates a cryptographically secure random UUID version 4.
 *
 * <p>UUID v4 is generated using cryptographically strong pseudo-random numbers and provides 122
 * bits of randomness, making collisions extremely unlikely in practice.
 *
 * <p>This function ignores the input value and generates a new UUID on each invocation.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * {
 *   "name": "uuid4"
 * }
 * }</pre>
 *
 * <p>Output format: {@code "550e8400-e29b-41d4-a716-446655440000"}
 *
 * @see UUID#randomUUID()
 */
public class Uuid4Function implements ValueFunction {

  /**
   * Generates a random UUID version 4.
   *
   * @param input the input value (ignored)
   * @param args function arguments (currently unused)
   * @return a new random UUID v4 as a string
   */
  @Override
  public Object apply(Object input, Map<String, Object> args) {
    return UUID.randomUUID().toString();
  }

  @Override
  public String name() {
    return "uuid4";
  }
}
