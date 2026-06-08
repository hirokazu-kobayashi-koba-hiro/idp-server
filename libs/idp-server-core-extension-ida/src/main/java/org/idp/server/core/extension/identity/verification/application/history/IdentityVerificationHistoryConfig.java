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
import org.idp.server.platform.json.JsonReadable;

public class IdentityVerificationHistoryConfig implements JsonReadable {

  List<HistoryFilter> filters = new ArrayList<>();

  public IdentityVerificationHistoryConfig() {}

  public List<HistoryFilter> filters() {
    return filters != null ? filters : new ArrayList<>();
  }

  public HistoryQueryPlan plan() {
    return HistoryQueryPlan.from(filters());
  }

  public boolean isEmpty() {
    return filters == null || filters.isEmpty();
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    List<Object> filterList = HistoryFilter.toMapList(filters);
    if (!filterList.isEmpty()) {
      map.put("filters", filterList);
    }
    return map;
  }
}
