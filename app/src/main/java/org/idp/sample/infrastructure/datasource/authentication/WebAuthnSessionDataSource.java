package org.idp.sample.infrastructure.datasource.authentication;

import com.webauthn4j.data.client.challenge.Challenge;
import jakarta.servlet.http.HttpSession;
import java.util.Objects;
import org.idp.sample.subdomain.webauthn.WebAuthnSession;
import org.idp.sample.subdomain.webauthn.WebAuthnSessionNotFoundException;
import org.idp.sample.subdomain.webauthn.WebAuthnSessionRepository;
import org.springframework.stereotype.Repository;

@Repository
public class WebAuthnSessionDataSource implements WebAuthnSessionRepository {

  HttpSession httpSession;

  public WebAuthnSessionDataSource(HttpSession httpSession) {
    this.httpSession = httpSession;
  }

  @Override
  public void register(WebAuthnSession webAuthnSession) {
    httpSession.setAttribute("WebAuthnChallenge", webAuthnSession.challenge());
  }

  @Override
  public WebAuthnSession get() {
    Challenge challenge = (Challenge) httpSession.getAttribute("WebAuthnChallenge");

    if (Objects.isNull(challenge)) {
      throw new WebAuthnSessionNotFoundException("not found web authn session");
    }
    return new WebAuthnSession(challenge);
  }
}
