/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.extension.identity.verification.application;

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
