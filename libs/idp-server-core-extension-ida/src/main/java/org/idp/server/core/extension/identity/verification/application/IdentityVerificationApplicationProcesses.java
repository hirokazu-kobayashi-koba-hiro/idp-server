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
