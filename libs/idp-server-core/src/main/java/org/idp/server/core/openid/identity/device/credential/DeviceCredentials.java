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

package org.idp.server.core.openid.identity.device.credential;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class DeviceCredentials implements Iterable<DeviceCredential> {
  List<DeviceCredential> values;

  public DeviceCredentials() {
    this.values = new ArrayList<>();
  }

  public DeviceCredentials(List<DeviceCredential> values) {
    this.values = values;
  }

  @Override
  public Iterator<DeviceCredential> iterator() {
    return values.iterator();
  }

  public boolean exists() {
    return values != null && !values.isEmpty();
  }

  public int size() {
    return values.size();
  }

  public Optional<DeviceCredential> findActiveCredential() {
    return values.stream().filter(DeviceCredential::isActive).findFirst();
  }

  public Optional<DeviceCredential> findActiveCredentialByAlgorithm(String algorithm) {
    return values.stream()
        .filter(DeviceCredential::isActive)
        .filter(credential -> credential.hasAlgorithm() && credential.algorithm().equals(algorithm))
        .findFirst();
  }

  public Optional<DeviceCredential> findById(DeviceCredentialIdentifier identifier) {
    return values.stream()
        .filter(credential -> credential.identifier().equals(identifier))
        .findFirst();
  }

  public DeviceCredentials filterActive() {
    List<DeviceCredential> activeCredentials =
        values.stream().filter(DeviceCredential::isActive).collect(Collectors.toList());
    return new DeviceCredentials(activeCredentials);
  }

  public DeviceCredentials add(DeviceCredential credential) {
    List<DeviceCredential> newValues = new ArrayList<>(values);
    newValues.add(credential);
    return new DeviceCredentials(newValues);
  }

  public List<Map<String, Object>> toMapList() {
    return values.stream().map(DeviceCredential::toMap).collect(Collectors.toList());
  }
}
