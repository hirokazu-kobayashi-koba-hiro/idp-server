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

package org.idp.server.core.extension.identity.verification.application.model;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class IdentityVerificationExaminationResults
    implements Iterable<IdentityVerificationExaminationResult> {

  List<IdentityVerificationExaminationResult> values;

  public IdentityVerificationExaminationResults() {
    this.values = new ArrayList<>();
  }

  public IdentityVerificationExaminationResults(
      List<IdentityVerificationExaminationResult> values) {
    this.values = values;
  }

  @Override
  public Iterator<IdentityVerificationExaminationResult> iterator() {
    return values.iterator();
  }

  public List<Map<String, Object>> toMapList() {
    return values.stream().map(IdentityVerificationExaminationResult::toMap).toList();
  }

  public IdentityVerificationExaminationResults add(
      IdentityVerificationExaminationResult identityVerificationExaminationResult) {
    List<IdentityVerificationExaminationResult> added = new ArrayList<>(values);
    added.add(identityVerificationExaminationResult);
    return new IdentityVerificationExaminationResults(added);
  }

  public boolean exists() {
    return values != null && !values.isEmpty();
  }
}
