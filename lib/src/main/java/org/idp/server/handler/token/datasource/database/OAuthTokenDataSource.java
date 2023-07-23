package org.idp.server.handler.token.datasource.database;

import java.util.Map;
import java.util.Objects;
import org.idp.server.basic.sql.SqlConnection;
import org.idp.server.basic.sql.SqlExecutor;
import org.idp.server.token.OAuthToken;
import org.idp.server.token.repository.OAuthTokenRepository;
import org.idp.server.type.oauth.AccessTokenValue;
import org.idp.server.type.oauth.RefreshTokenValue;
import org.idp.server.type.oauth.TokenIssuer;

public class OAuthTokenDataSource implements OAuthTokenRepository {

  SqlConnection sqlConnection;

  public OAuthTokenDataSource(SqlConnection sqlConnection) {
    this.sqlConnection = sqlConnection;
  }

  @Override
  public void register(OAuthToken oAuthToken) {
    SqlExecutor sqlExecutor = new SqlExecutor(sqlConnection.connection());
    String sql = InsertSqlCreator.createInsert(oAuthToken);
    sqlExecutor.execute(sql);
  }

  @Override
  public OAuthToken find(TokenIssuer tokenIssuer, AccessTokenValue accessTokenValue) {
    SqlExecutor sqlExecutor = new SqlExecutor(sqlConnection.connection());
    String sqlTemplate =
        """
        SELECT id, token_issuer, token_type, access_token, user_id, user_payload, authentication, client_id, scopes, claims, custom_properties, authorization_details, expires_in, access_token_expired_at, access_token_created_at, refresh_token, refresh_token_expired_at, refresh_token_created_at, id_token, client_certification_thumbprint, c_nonce, c_nonce_expires_in
        FROM oauth_token
        WHERE token_issuer = '%s' AND access_token = '%s';
        """;
    String sql = String.format(sqlTemplate, tokenIssuer.value(), accessTokenValue.value());
    Map<String, String> stringMap = sqlExecutor.selectOne(sql);
    if (Objects.isNull(stringMap) || stringMap.isEmpty()) {
      return new OAuthToken();
    }
    return ModelConverter.convert(stringMap);
  }

  @Override
  public OAuthToken find(TokenIssuer tokenIssuer, RefreshTokenValue refreshTokenValue) {
    SqlExecutor sqlExecutor = new SqlExecutor(sqlConnection.connection());
    String sqlTemplate =
        """
            SELECT id, token_issuer, token_type, access_token, user_id, user_payload, authentication, client_id, scopes, claims, custom_properties, authorization_details, expires_in, access_token_expired_at, access_token_created_at, refresh_token, refresh_token_expired_at, refresh_token_created_at, id_token, client_certification_thumbprint, c_nonce, c_nonce_expires_in
            FROM oauth_token
            WHERE token_issuer = '%s' AND refresh_token = '%s';
            """;
    String sql = String.format(sqlTemplate, tokenIssuer.value(), refreshTokenValue.value());
    Map<String, String> stringMap = sqlExecutor.selectOne(sql);
    if (Objects.isNull(stringMap) || stringMap.isEmpty()) {
      return new OAuthToken();
    }
    return ModelConverter.convert(stringMap);
  }

  @Override
  public void delete(OAuthToken oAuthToken) {
    SqlExecutor sqlExecutor = new SqlExecutor(sqlConnection.connection());
    String sqlTemplate = """
            DELETE FROM oauth_token WHERE id = '%s';
            """;
    String sql = String.format(sqlTemplate, oAuthToken.identifier().value());
    sqlExecutor.execute(sql);
  }
}
