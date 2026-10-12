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

package org.idp.server.platform.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A key of the flat map that {@link ObjectCompositor} composes into a nested structure.
 *
 * <p>A key is a list of segments. A name segment is a map key, and an index segment is a list
 * index. Two kinds of key are built:
 *
 * <ul>
 *   <li>{@link #path(String)} — a mapping rule's {@code to}, such as {@code
 *       "verification.evidence.0.type"}. It is split on {@code .}, and an all-digit segment is a
 *       list index.
 *   <li>{@link #literal(String)} — a key copied by {@code to: "*"}. It is used as-is: a single name
 *       segment, even if it contains {@code .} or digits (Issue #1971).
 * </ul>
 *
 * <p>A path without {@code .} or digits and a literal of the same text are equal, so a later rule
 * replaces an earlier one regardless of which kind wrote it.
 */
public class CompositionKey {

  List<Segment> segments;

  private CompositionKey(List<Segment> segments) {
    this.segments = segments;
  }

  public static CompositionKey path(String path) {
    List<Segment> segments = new ArrayList<>();
    for (String name : path.split("\\.")) {
      segments.add(name.matches("\\d+") ? Segment.index(name) : Segment.name(name));
    }
    return new CompositionKey(segments);
  }

  public static CompositionKey literal(String key) {
    return new CompositionKey(List.of(Segment.name(key)));
  }

  List<Segment> segments() {
    return segments;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    CompositionKey that = (CompositionKey) o;
    return Objects.equals(segments, that.segments);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(segments);
  }

  @Override
  public String toString() {
    return segments.stream().map(Segment::value).reduce((a, b) -> a + "." + b).orElse("");
  }

  /** A map key, or a list index. */
  record Segment(String value, boolean isIndex) {

    static Segment name(String value) {
      return new Segment(value, false);
    }

    static Segment index(String value) {
      return new Segment(value, true);
    }
  }
}
