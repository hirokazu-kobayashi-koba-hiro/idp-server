package org.idp.sample.infrastructure.datasource.authentication;

import jakarta.servlet.http.HttpSession;
import java.util.Objects;
import org.idp.sample.subdomain.webauthn.WebAuthnHttpSessionRepository;
import org.idp.sample.subdomain.webauthn.WebAuthnSession;
import org.idp.sample.subdomain.webauthn.WebAuthnSessionNotFoundException;
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
    WebAuthnSession webAuthnSession =
        (WebAuthnSession) httpSession.getAttribute("WebAuthnSession");

    if (Objects.isNull(webAuthnSession)) {
      throw new WebAuthnSessionNotFoundException("not found web authn session");
    }
    return webAuthnSession;
  }
}
