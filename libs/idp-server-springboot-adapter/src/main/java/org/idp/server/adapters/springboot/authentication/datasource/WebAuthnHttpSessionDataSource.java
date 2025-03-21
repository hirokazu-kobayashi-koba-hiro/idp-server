package org.idp.server.adapters.springboot.authentication.datasource;

import jakarta.servlet.http.HttpSession;
import java.util.Objects;
import org.idp.server.authenticators.webauthn.WebAuthnHttpSessionRepository;
import org.idp.server.authenticators.webauthn.WebAuthnSession;
import org.idp.server.authenticators.webauthn.WebAuthnSessionNotFoundException;
import org.springframework.stereotype.Repository;

@Repository
public class WebAuthnHttpSessionDataSource implements WebAuthnHttpSessionRepository {

  HttpSession httpSession;

  public WebAuthnHttpSessionDataSource(HttpSession httpSession) {
    this.httpSession = httpSession;
  }

  @Override
  public void register(WebAuthnSession webAuthnSession) {
    httpSession.setAttribute("WebAuthnSession", webAuthnSession);
  }

  @Override
  public WebAuthnSession get() {
    WebAuthnSession webAuthnSession = (WebAuthnSession) httpSession.getAttribute("WebAuthnSession");

    if (Objects.isNull(webAuthnSession)) {
      throw new WebAuthnSessionNotFoundException("not found web authn session");
    }
    return webAuthnSession;
  }
}
