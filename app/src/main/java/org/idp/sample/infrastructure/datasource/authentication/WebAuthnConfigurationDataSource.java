package org.idp.sample.infrastructure.datasource.authentication;

import java.util.Objects;
import org.idp.sample.domain.model.tenant.Tenant;
import org.idp.sample.subdomain.webauthn.WebAuthnConfiguration;
import org.idp.sample.subdomain.webauthn.WebAuthnConfigurationNotFoundException;
import org.idp.sample.subdomain.webauthn.WebAuthnConfigurationRepository;
import org.springframework.stereotype.Repository;

@Repository
public class WebAuthnConfigurationDataSource implements WebAuthnConfigurationRepository {

  WebAuthnConfigurationMapper mapper;

  public WebAuthnConfigurationDataSource(WebAuthnConfigurationMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public WebAuthnConfiguration get(Tenant tenant) {
    WebAuthnConfiguration webAuthnConfiguration = mapper.selectBy(tenant.identifier());

    if (Objects.isNull(webAuthnConfiguration)) {
      throw new WebAuthnConfigurationNotFoundException(
          String.format("Web Authn Configuration is Not Found (%s)", tenant.identifierValue()));
    }

    return webAuthnConfiguration;
  }
}
