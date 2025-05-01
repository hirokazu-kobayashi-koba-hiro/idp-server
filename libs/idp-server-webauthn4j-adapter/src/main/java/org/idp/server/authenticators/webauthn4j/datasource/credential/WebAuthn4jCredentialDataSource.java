package org.idp.server.authenticators.webauthn4j.datasource.credential;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.idp.server.authenticators.webauthn4j.WebAuthn4jCredential;
import org.idp.server.authenticators.webauthn4j.WebAuthn4jCredentialRepository;
import org.idp.server.authenticators.webauthn4j.WebAuthn4jCredentials;
import org.idp.server.basic.datasource.SqlExecutor;

public class WebAuthn4jCredentialDataSource implements WebAuthn4jCredentialRepository {

  @Override
  public void register(WebAuthn4jCredential credential) {
    SqlExecutor sqlExecutor = new SqlExecutor();
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
  public WebAuthn4jCredentials findAll(String userId) {
    SqlExecutor sqlExecutor = new SqlExecutor();

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
      return new WebAuthn4jCredentials();
    }

    List<WebAuthn4jCredential> credentials =
        results.stream().map(ModelConverter::convert).collect(Collectors.toList());

    return new WebAuthn4jCredentials(credentials);
  }

  @Override
  public void updateSignCount(String credentialId, long signCount) {}

  @Override
  public void delete(String credentialId) {}
}
