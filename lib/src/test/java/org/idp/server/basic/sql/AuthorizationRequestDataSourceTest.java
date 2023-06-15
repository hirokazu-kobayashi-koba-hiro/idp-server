package org.idp.server.basic.sql;

import java.util.UUID;
import org.idp.server.handler.oauth.datasource.database.AuthorizationRequestDataSource;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.oauth.request.AuthorizationRequestBuilder;
import org.idp.server.oauth.request.AuthorizationRequestIdentifier;
import org.junit.jupiter.api.Test;

public class AuthorizationRequestDataSourceTest {

  SqlConnection sqlConnection =
      new SqlConnection("jdbc:postgresql://localhost:5432/idpserver", "idpserver", "idpserver");
  AuthorizationRequestDataSource authorizationRequestDataSource =
      new AuthorizationRequestDataSource(sqlConnection);

  @Test
  void register() {
    AuthorizationRequest request =
        new AuthorizationRequestBuilder()
            .add(new AuthorizationRequestIdentifier(UUID.randomUUID().toString()))
            .build();
    authorizationRequestDataSource.register(request);
  }
}
