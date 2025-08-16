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

import java.security.SecureRandom;
import java.util.Map;

public class RandomStringFunction implements ValueFunction {

  private static final String DEFAULT_ALPHABET =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
  private final SecureRandom random = new SecureRandom();

  @Override
  public Object apply(Object input, Map<String, Object> args) {
    int length = ((Number) args.getOrDefault("length", 16)).intValue();
    String alphabet = (String) args.getOrDefault("alphabet", DEFAULT_ALPHABET);
    StringBuilder sb = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      int idx = random.nextInt(alphabet.length());
      sb.append(alphabet.charAt(idx));
    }

    return sb.toString();
  }

  @Override
  public String name() {
    return "randomString";
  }
}
