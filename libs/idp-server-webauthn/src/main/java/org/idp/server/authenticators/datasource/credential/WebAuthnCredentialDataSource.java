package org.idp.server.authenticators.datasource.credential;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.idp.server.authenticators.webauthn.WebAuthnCredential;
import org.idp.server.authenticators.webauthn.WebAuthnCredentialRepository;
import org.idp.server.authenticators.webauthn.WebAuthnCredentials;
import org.idp.server.core.basic.sql.SqlExecutor;
import org.idp.server.core.basic.sql.TransactionManager;

public class WebAuthnCredentialDataSource implements WebAuthnCredentialRepository {

  @Override
  public void register(WebAuthnCredential credential) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());
    String sqlTemplate =
        """
            INSERT INTO public.webauthn_credentials (id, idp_user_id, rp_id, attestation_object, sign_count)
            VALUES (?, ?, ?, ?, ?);
            """;
    List<Object> params = new ArrayList<>();
    params.add(credential.id());
    params.add(credential.userId());
    params.add(credential.rpId());
    params.add(credential.attestationObject());
    params.add(credential.signCount());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public WebAuthnCredentials findAll(String userId) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());

    String sqlTemplate =
        """
            SELECT id, idp_user_id, rp_id, attestation_object, sign_count
            FROM webauthn_credentials
            WHERE idp_user_id = ?;
            """;
    List<Object> params = new ArrayList<>();
    params.add(userId);

    List<Map<String, Object>> results = sqlExecutor.selectListWithType(sqlTemplate, params);

    if (Objects.isNull(results) || results.isEmpty()) {
      return new WebAuthnCredentials();
    }

    List<WebAuthnCredential> credentials =
        results.stream().map(ModelConverter::convert).collect(Collectors.toList());

    return new WebAuthnCredentials(credentials);
  }

  @Override
  public void updateSignCount(String credentialId, long signCount) {}

  @Override
  public void delete(String credentialId) {}
}
