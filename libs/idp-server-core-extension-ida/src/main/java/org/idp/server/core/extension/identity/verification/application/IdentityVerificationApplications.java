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

package org.idp.server.core.extension.identity.verification.application;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.idp.server.core.extension.identity.verification.IdentityVerificationType;

public class IdentityVerificationApplications implements Iterable<IdentityVerificationApplication> {

  List<IdentityVerificationApplication> values;

  public IdentityVerificationApplications() {
    this.values = new ArrayList<>();
  }

  public IdentityVerificationApplications(List<IdentityVerificationApplication> values) {
    this.values = values;
  }

  @Override
  public Iterator<IdentityVerificationApplication> iterator() {
    return values.iterator();
  }

  public boolean containsRunningState(IdentityVerificationType type) {
    return values.stream()
        .anyMatch(
            application ->
                application.identityVerificationType().equals(type) && application.isRunning());
  }

  public IdentityVerificationApplications filterApproved(List<String> targeTypes) {
    List<IdentityVerificationApplication> filtered =
        values.stream()
            .filter(application -> targeTypes.contains(application.identityVerificationType.name()))
            .toList();
    return new IdentityVerificationApplications(filtered);
  }

  public boolean containsApproved(List<String> targeTypes) {
    return values.stream()
        .anyMatch(application -> targeTypes.contains(application.identityVerificationType.name()));
  }

  public List<Map<String, Object>> toList() {
    return values.stream().map(IdentityVerificationApplication::toMap).toList();
  }
}
