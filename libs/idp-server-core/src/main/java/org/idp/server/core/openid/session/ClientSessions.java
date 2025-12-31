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
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ClientSessions implements Iterable<ClientSession> {

  private final List<ClientSession> values;

  public ClientSessions() {
    this.values = new ArrayList<>();
  }

  public ClientSessions(List<ClientSession> values) {
    this.values = values;
  }

  public static ClientSessions empty() {
    return new ClientSessions();
  }

  public List<ClientSession> values() {
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

  public ClientSessions filterActive() {
    List<ClientSession> filtered =
        values.stream().filter(ClientSession::isActive).collect(Collectors.toList());
    return new ClientSessions(filtered);
  }

  public ClientSessions excludeClient(String clientId) {
    List<ClientSession> filtered =
        values.stream()
            .filter(session -> !session.clientId().equals(clientId))
            .collect(Collectors.toList());
    return new ClientSessions(filtered);
  }

  public List<String> clientIds() {
    return values.stream().map(ClientSession::clientId).distinct().collect(Collectors.toList());
  }

  public Stream<ClientSession> stream() {
    return values.stream();
  }

  @Override
  public Iterator<ClientSession> iterator() {
    return values.iterator();
  }
}
