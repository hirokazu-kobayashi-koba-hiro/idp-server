package org.idp.server.authenticators.webauthn4j;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.idp.server.core.authentication.webauthn.WebAuthnCredentialNotFoundException;

public class WebAuthn4jCredentials implements Iterable<WebAuthn4jCredential> {

  List<WebAuthn4jCredential> values;

  public WebAuthn4jCredentials() {
    this.values = new ArrayList<>();
  }

  public WebAuthn4jCredentials(List<WebAuthn4jCredential> values) {
    this.values = values;
  }

  @Override
  public Iterator<WebAuthn4jCredential> iterator() {
    return values.iterator();
  }

  public WebAuthn4jCredential get(String rpId) {
    return values.stream()
        .filter(item -> item.rpId().equals(rpId))
        .findFirst()
        .orElseThrow(
            () -> new WebAuthnCredentialNotFoundException("No credential found for " + rpId));
  }
}
