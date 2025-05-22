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

public class IdentityVerificationApplicationProcesses
    implements Iterable<IdentityVerificationApplicationProcess> {

  List<IdentityVerificationApplicationProcess> values;

  public IdentityVerificationApplicationProcesses() {
    this.values = new ArrayList<>();
  }

  public IdentityVerificationApplicationProcesses(
      List<IdentityVerificationApplicationProcess> values) {
    this.values = values;
  }

  public IdentityVerificationApplicationProcesses add(
      IdentityVerificationApplicationProcess process) {
    ArrayList<IdentityVerificationApplicationProcess> added = new ArrayList<>(values);
    added.add(process);
    return new IdentityVerificationApplicationProcesses(added);
  }

  public boolean exists() {
    return values != null && !values.isEmpty();
  }

  @Override
  public Iterator<IdentityVerificationApplicationProcess> iterator() {
    return values.iterator();
  }

  public List<IdentityVerificationApplicationProcess> toList() {
    return values;
  }

  public List<Map<String, Object>> toMapList() {
    return values.stream().map(IdentityVerificationApplicationProcess::toMap).toList();
  }
}
