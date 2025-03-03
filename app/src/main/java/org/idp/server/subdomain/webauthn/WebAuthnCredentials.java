package org.idp.server.subdomain.webauthn;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class WebAuthnCredentials implements Iterable<WebAuthnCredential> {

  List<WebAuthnCredential> values;

  public WebAuthnCredentials() {
    this.values = new ArrayList<>();
  }

  public WebAuthnCredentials(List<WebAuthnCredential> values) {
    this.values = values;
  }

  @Override
  public Iterator<WebAuthnCredential> iterator() {
    return values.iterator();
  }

  public WebAuthnCredential get(String rpId) {
    return values.stream()
        .filter(item -> item.rpId().equals(rpId))
        .findFirst()
        .orElseThrow(
            () -> new WebAuthnCredentialNotFoundException("No credential found for " + rpId));
  }
}
