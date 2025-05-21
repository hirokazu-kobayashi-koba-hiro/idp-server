package org.idp.server.authentication;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.core.oidc.authentication.legacy.UserInfoMapper;
import org.idp.server.core.oidc.authentication.legacy.UserInfoMappingRule;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.identity.address.Address;
import org.junit.jupiter.api.Test;

public class UserInfoMapperTest {

  @Test
  public void testBasicUserMapping() throws Exception {
    String json =
        "{\"email\": \"test@example.com\", \"name\": \"Test User\", \"email_verified\": true}";
    ObjectMapper mapper = new ObjectMapper();
    JsonNodeWrapper body = new JsonNodeWrapper(mapper.readTree(json));

    List<UserInfoMappingRule> rules =
        List.of(
            new UserInfoMappingRule("email", "email", "string"),
            new UserInfoMappingRule("name", "name", "string"),
            new UserInfoMappingRule("email_verified", "email_verified", "boolean"));

    UserInfoMapper userInfoMapper = new UserInfoMapper("test", body, rules);
    User user = userInfoMapper.toUser();

    assertEquals("test@example.com", user.email());
    assertEquals("Test User", user.name());
    assertTrue(user.emailVerified());
  }

  @Test
  public void testAddressMapping() throws Exception {
    String json =
        "{\"address\": {\"formatted\": \"123 Main St\", \"street_address\": \"123 Main St\", \"locality\": \"Springfield\", \"region\": \"IL\", \"postal_code\": \"62704\", \"country\": \"USA\"}}";
    ObjectMapper mapper = new ObjectMapper();
    JsonNodeWrapper body = new JsonNodeWrapper(mapper.readTree(json));

    List<UserInfoMappingRule> rules =
        List.of(new UserInfoMappingRule("address", "address", "address"));

    UserInfoMapper userInfoMapper = new UserInfoMapper("test", body, rules);
    User user = userInfoMapper.toUser();
    Address address = user.address();

    assertNotNull(address);
    assertEquals("123 Main St", address.formatted());
    assertEquals("Springfield", address.locality());
    assertEquals("IL", address.region());
    assertEquals("62704", address.postalCode());
    assertEquals("USA", address.country());
  }
}
