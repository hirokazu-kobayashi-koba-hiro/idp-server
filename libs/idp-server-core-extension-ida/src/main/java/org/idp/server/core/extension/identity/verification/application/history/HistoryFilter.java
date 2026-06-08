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

package org.idp.server.core.extension.identity.verification.application.history;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplicationStatus;
import org.idp.server.platform.json.JsonReadable;

/**
 * One filter specification declaring a subset of past applications that must be fetched. Multiple
 * filters are combined with OR at the SQL level so the repository fetches the union in a single
 * statement, and the {@code Applications} model is left as the boundary where verifiers express
 * their domain judgement.
 */
public class HistoryFilter implements JsonReadable {

  /** Shorthand keyword for the four "still being processed" statuses. */
  public static final String STATUSES_RUNNING = "running";

  List<String> types = new ArrayList<>();
  Object statuses;

  public HistoryFilter() {}

  public List<String> types() {
    return types != null ? types : new ArrayList<>();
  }

  /**
   * Resolve {@code statuses} into a concrete status value list. Supports a single keyword string
   * (currently only {@code "running"}) or an explicit list of status values.
   */
  public List<String> resolvedStatuses() {
    if (statuses == null) {
      return List.of();
    }
    if (statuses instanceof String keyword) {
      if (STATUSES_RUNNING.equals(keyword)) {
        return IdentityVerificationApplicationStatus.runningValues();
      }
      return List.of(keyword);
    }
    if (statuses instanceof List<?> list) {
      List<String> values = new ArrayList<>();
      for (Object item : list) {
        if (item != null) {
          values.add(item.toString());
        }
      }
      return values;
    }
    return List.of();
  }

  public boolean isEmpty() {
    return types().isEmpty() || resolvedStatuses().isEmpty();
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("types", types());
    if (statuses != null) {
      map.put("statuses", statuses);
    }
    return map;
  }

  /** Serialize a list of filters for {@code toMap()} output, dropping empty entries. */
  public static List<Object> toMapList(List<HistoryFilter> filters) {
    List<Object> serialized = new ArrayList<>();
    if (filters == null) {
      return serialized;
    }
    for (HistoryFilter filter : filters) {
      if (filter != null && !filter.isEmpty()) {
        serialized.add(filter.toMap());
      }
    }
    return serialized;
  }
}
