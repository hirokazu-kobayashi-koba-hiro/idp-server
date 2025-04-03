package org.idp.server.authenticators.datasource.configuration;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.idp.server.authenticators.webauthn.WebAuthnConfiguration;
import org.idp.server.authenticators.webauthn.WebAuthnConfigurationNotFoundException;
import org.idp.server.authenticators.webauthn.WebAuthnConfigurationRepository;
import org.idp.server.core.basic.sql.SqlExecutor;
import org.idp.server.core.basic.sql.TransactionManager;
import org.idp.server.core.tenant.Tenant;

public class WebAuthnConfigurationDataSource implements WebAuthnConfigurationRepository {

  @Override
  public WebAuthnConfiguration get(Tenant tenant) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());
    String sqlTemplate =
        """
            SELECT rp_id, rp_name, origin, attestation_preference, authenticator_attachment, require_resident_key, user_verification_required, user_presence_required
            FROM webauthn_rp_configuration
            WHERE tenant_id = ?
            """;

    List<Object> params = List.of(tenant.identifierValue());
    Map<String, String> result = sqlExecutor.selectOne(sqlTemplate, params);

    if (Objects.isNull(result) || result.isEmpty()) {
      throw new WebAuthnConfigurationNotFoundException(
          String.format("Web Authn Configuration is Not Found (%s)", tenant.identifierValue()));
    }

    return ModelConverter.convert(result);
  }
}
