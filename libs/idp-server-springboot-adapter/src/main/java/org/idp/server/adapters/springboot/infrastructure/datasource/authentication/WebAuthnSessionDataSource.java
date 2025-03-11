package org.idp.server.adapters.springboot.infrastructure.datasource.authentication;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.adapters.springboot.subdomain.webauthn.WebAuthnSession;
import org.idp.server.adapters.springboot.subdomain.webauthn.WebAuthnSessionNotFoundException;
import org.idp.server.adapters.springboot.subdomain.webauthn.WebAuthnSessionRepository;
import org.springframework.stereotype.Repository;

@Repository
public class WebAuthnSessionDataSource implements WebAuthnSessionRepository {

  Map<String, WebAuthnSession> map = new HashMap<>();

  @Override
  public void register(WebAuthnSession webAuthnSession) {
    if (map.size() > 3) {
      map.clear();
    }
    map.put("WebAuthnSession", webAuthnSession);
  }

  @Override
  public WebAuthnSession get() {
    WebAuthnSession webAuthnSession = map.get("WebAuthnSession");

    if (Objects.isNull(webAuthnSession)) {
      throw new WebAuthnSessionNotFoundException("not found web authn session");
    }
    return webAuthnSession;
  }
}
