package org.idp.server.core.oidc.rar;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.idp.server.core.oidc.vc.CredentialDefinition;

public class AuthorizationDetails implements Iterable<AuthorizationDetail> {

  List<AuthorizationDetail> values;

  public AuthorizationDetails() {
    this.values = new ArrayList<>();
  }

  public AuthorizationDetails(List<AuthorizationDetail> values) {
    this.values = values;
  }

  @Override
  public Iterator<AuthorizationDetail> iterator() {
    return values.iterator();
  }

  public boolean exists() {
    return !values.isEmpty();
  }

  public List<AuthorizationDetail> values() {
    return values;
  }

  public List<Map<String, Object>> toMapValues() {
    return values.stream().map(AuthorizationDetail::values).toList();
  }

  public boolean hasVerifiableCredential() {
    return values.stream().anyMatch(AuthorizationDetail::isVerifiableCredential);
  }

  public List<CredentialDefinition> credentialDefinitions() {
    return values.stream().map(AuthorizationDetail::credentialDefinition).toList();
  }
}
