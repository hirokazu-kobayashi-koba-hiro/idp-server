package org.idp.server.handler.token.datasource.database;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.idp.server.basic.sql.SqlExecutor;
import org.idp.server.basic.sql.TransactionManager;
import org.idp.server.token.OAuthToken;
import org.idp.server.token.repository.OAuthTokenRepository;
import org.idp.server.type.oauth.AccessTokenEntity;
import org.idp.server.type.oauth.RefreshTokenEntity;
import org.idp.server.type.oauth.TokenIssuer;

public class OAuthTokenDataSource implements OAuthTokenRepository {

  @Override
  public void register(OAuthToken oAuthToken) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());
    String sqlTemplate =
        """
                    INSERT INTO public.oauth_token (id, token_issuer, token_type, access_token, user_id, user_payload, authentication, client_id, scopes, claims, custom_properties, authorization_details, expires_in, access_token_expired_at, access_token_created_at, refresh_token, refresh_token_expired_at, refresh_token_created_at, id_token, client_certification_thumbprint, c_nonce, c_nonce_expires_in)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
                    """;
    List<Object> params = InsertSqlParamsCreator.create(oAuthToken);
    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public OAuthToken find(TokenIssuer tokenIssuer, AccessTokenEntity accessTokenEntity) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());
    String sqlTemplate =
        """
        SELECT id, token_issuer, token_type, access_token, user_id, user_payload, authentication, client_id, scopes, claims, custom_properties, authorization_details, expires_in, access_token_expired_at, access_token_created_at, refresh_token, refresh_token_expired_at, refresh_token_created_at, id_token, client_certification_thumbprint, c_nonce, c_nonce_expires_in
        FROM oauth_token
        WHERE token_issuer = ? AND access_token = ?;
        """;
    List<Object> params = List.of(tokenIssuer.value(), accessTokenEntity.value());
    Map<String, String> stringMap = sqlExecutor.selectOne(sqlTemplate, params);

    if (Objects.isNull(stringMap) || stringMap.isEmpty()) {
      return new OAuthToken();
    }

    return ModelConverter.convert(stringMap);
  }

  @Override
  public OAuthToken find(TokenIssuer tokenIssuer, RefreshTokenEntity refreshTokenEntity) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());
    String sqlTemplate =
        """
            SELECT id, token_issuer, token_type, access_token, user_id, user_payload, authentication, client_id, scopes, claims, custom_properties, authorization_details, expires_in, access_token_expired_at, access_token_created_at, refresh_token, refresh_token_expired_at, refresh_token_created_at, id_token, client_certification_thumbprint, c_nonce, c_nonce_expires_in
            FROM oauth_token
            WHERE token_issuer = ? AND refresh_token = ?;
            """;
    List<Object> params = List.of(tokenIssuer.value(), refreshTokenEntity.value());
    Map<String, String> stringMap = sqlExecutor.selectOne(sqlTemplate, params);

    if (Objects.isNull(stringMap) || stringMap.isEmpty()) {
      return new OAuthToken();
    }

    return ModelConverter.convert(stringMap);
  }

  @Override
  public void delete(OAuthToken oAuthToken) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());
    String sqlTemplate = """
            DELETE FROM oauth_token WHERE id = ?;
            """;
    List<Object> params = List.of(oAuthToken.identifier().value());

    sqlExecutor.execute(sqlTemplate, params);
  }
}
