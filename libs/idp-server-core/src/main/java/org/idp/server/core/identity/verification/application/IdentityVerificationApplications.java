package org.idp.server.core.identity.verification.application;

import org.idp.server.core.identity.verification.IdentityVerificationType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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
    return values.stream().anyMatch(application -> application.identityVerificationType().equals(type)
            && application.isRunning());
  }
}
