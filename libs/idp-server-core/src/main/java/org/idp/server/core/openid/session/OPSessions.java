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

package org.idp.server.core.openid.session;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OPSessions implements Iterable<OPSession> {

  private final List<OPSession> values;

  public OPSessions() {
    this.values = new ArrayList<>();
  }

  public OPSessions(List<OPSession> values) {
    this.values = values;
  }

  public static OPSessions empty() {
    return new OPSessions();
  }

  public List<OPSession> values() {
    return values;
  }

  public int size() {
    return values.size();
  }

  public boolean isEmpty() {
    return values.isEmpty();
  }

  public boolean isNotEmpty() {
    return !values.isEmpty();
  }

  public OPSessions filterActive() {
    List<OPSession> filtered =
        values.stream().filter(OPSession::isActive).collect(Collectors.toList());
    return new OPSessions(filtered);
  }

  public OPSessions sortByCreatedAtAsc() {
    List<OPSession> sorted =
        values.stream()
            .sorted(Comparator.comparing(OPSession::createdAt))
            .collect(Collectors.toList());
    return new OPSessions(sorted);
  }

  public OPSessions sortByCreatedAtDesc() {
    List<OPSession> sorted =
        values.stream()
            .sorted(Comparator.comparing(OPSession::createdAt).reversed())
            .collect(Collectors.toList());
    return new OPSessions(sorted);
  }

  public OPSessions oldestSessions(int count) {
    List<OPSession> oldest =
        values.stream()
            .sorted(Comparator.comparing(OPSession::createdAt))
            .limit(count)
            .collect(Collectors.toList());
    return new OPSessions(oldest);
  }

  public Stream<OPSession> stream() {
    return values.stream();
  }

  @Override
  public Iterator<OPSession> iterator() {
    return values.iterator();
  }
}
