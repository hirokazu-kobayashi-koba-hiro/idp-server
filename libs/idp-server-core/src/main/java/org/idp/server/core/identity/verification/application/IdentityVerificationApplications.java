package org.idp.server.core.identity.verification.application;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.idp.server.core.identity.verification.IdentityVerificationType;

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

  public boolean containsApprovedTarget(List<String> targeTypes) {
    return values.stream()
        .anyMatch(application -> targeTypes.contains(application.identityVerificationType.name()));
  }
}
