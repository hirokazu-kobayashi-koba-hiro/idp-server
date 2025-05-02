package org.idp.server.core.verifiable_credential.request;

import java.util.Iterator;
import java.util.List;

public class BatchCredentialRequests implements Iterable<VerifiableCredentialRequest> {

  List<VerifiableCredentialRequest> values;

  public BatchCredentialRequests() {
    this.values = List.of();
  }

  public BatchCredentialRequests(List<VerifiableCredentialRequest> values) {
    this.values = values;
  }

  @Override
  public Iterator<VerifiableCredentialRequest> iterator() {
    return values.iterator();
  }
}
