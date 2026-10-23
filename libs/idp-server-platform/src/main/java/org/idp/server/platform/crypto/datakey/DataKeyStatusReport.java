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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Which key encryption keys still wrap data keys.
 *
 * <p>A key encryption key can be removed from the configuration once no data key is wrapped by it.
 * A count under a key id this server does not hold means those data keys cannot be opened.
 */
public class DataKeyStatusReport {

  String activeKekId;
  List<String> configuredKekIds;
  Map<String, Long> dataKeysByKekId;

  public DataKeyStatusReport(
      String activeKekId, List<String> configuredKekIds, Map<String, Long> dataKeysByKekId) {
    this.activeKekId = activeKekId;
    this.configuredKekIds = configuredKekIds;
    this.dataKeysByKekId = dataKeysByKekId;
  }

  /** The key encryption key the answering server wraps new data keys with. */
  public String activeKekId() {
    return activeKekId;
  }

  /** Data keys wrapped by a key encryption key other than the active one. */
  public long pendingRewrap() {
    return dataKeysByKekId.entrySet().stream()
        .filter(entry -> !entry.getKey().equals(activeKekId))
        .mapToLong(Map.Entry::getValue)
        .sum();
  }

  /** Data keys wrapped by a key encryption key this server does not hold. */
  public long unopenable() {
    return dataKeysByKekId.entrySet().stream()
        .filter(entry -> !configuredKekIds.contains(entry.getKey()))
        .mapToLong(Map.Entry::getValue)
        .sum();
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new HashMap<>();
    result.put("active_key_id", activeKekId);
    result.put(
        "key_encryption_keys",
        dataKeysByKekId.entrySet().stream()
            .map(
                entry ->
                    Map.<String, Object>of(
                        "key_id", entry.getKey(),
                        "data_keys", entry.getValue(),
                        "configured", configuredKekIds.contains(entry.getKey())))
            .toList());
    result.put("configured_key_ids", configuredKekIds);
    result.put("pending_rewrap", pendingRewrap());
    result.put("unopenable", unopenable());
    return result;
  }
}
