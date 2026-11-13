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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * What a sealed value belongs to, authenticated with it but not stored in it.
 *
 * <p>Opening has to name the same owner. A ciphertext copied to another owner's row, or moved to
 * another field of the same row, does not open, even under the same tenant and data key.
 *
 * <p>Each entry is written with its length, so no choice of values can make two different contexts
 * produce the same bytes.
 */
public class SealingContext {

  List<String> entries;

  SealingContext(List<String> entries) {
    this.entries = entries;
  }

  public static SealingContext empty() {
    return new SealingContext(List.of());
  }

  public SealingContext with(String name, String value) {
    if (value == null) {
      throw new IllegalArgumentException("Sealing context value is missing: " + name);
    }
    List<String> next = new ArrayList<>(entries);
    next.add(name.length() + ":" + name + "=" + value.length() + ":" + value);
    return new SealingContext(List.copyOf(next));
  }

  byte[] bytes() {
    return String.join("|", entries).getBytes(StandardCharsets.UTF_8);
  }
}
