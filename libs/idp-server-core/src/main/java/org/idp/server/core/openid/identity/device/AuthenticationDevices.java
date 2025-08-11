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

package org.idp.server.core.openid.identity.device;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AuthenticationDevices implements Iterable<AuthenticationDevice> {
  List<AuthenticationDevice> values;

  public AuthenticationDevices() {
    this.values = new ArrayList<>();
  }

  public AuthenticationDevices(List<AuthenticationDevice> values) {
    this.values = values;
  }

  @Override
  public Iterator<AuthenticationDevice> iterator() {
    return values.iterator();
  }

  public List<Map<String, Object>> toMapList() {
    return values.stream().map(AuthenticationDevice::toMap).collect(Collectors.toList());
  }
}
