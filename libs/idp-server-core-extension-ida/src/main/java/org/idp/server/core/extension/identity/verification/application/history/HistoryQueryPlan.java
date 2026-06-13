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
import java.util.List;

/**
 * Execution-time projection of the {@code history} config section. Carries the filter list the
 * repository should fetch the union of, with empty filters already dropped.
 */
public class HistoryQueryPlan {

  List<HistoryFilter> filters;

  HistoryQueryPlan(List<HistoryFilter> filters) {
    this.filters = filters;
  }

  public static HistoryQueryPlan from(List<HistoryFilter> source) {
    List<HistoryFilter> effective = new ArrayList<>();
    if (source != null) {
      for (HistoryFilter filter : source) {
        // Drop empty filters, and de-duplicate equal ones so a fallback filter merged on top of an
        // explicit history section does not produce a redundant OR clause.
        if (filter != null && !filter.isEmpty() && !effective.contains(filter)) {
          effective.add(filter);
        }
      }
    }
    return new HistoryQueryPlan(effective);
  }

  public List<HistoryFilter> filters() {
    return filters;
  }

  public boolean isEmpty() {
    return filters.isEmpty();
  }
}
