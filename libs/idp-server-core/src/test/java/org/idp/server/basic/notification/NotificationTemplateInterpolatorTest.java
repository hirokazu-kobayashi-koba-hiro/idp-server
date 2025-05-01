package org.idp.server.basic.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.notification.NotificationTemplateInterpolator;
import org.junit.jupiter.api.Test;

public class NotificationTemplateInterpolatorTest {

  @Test
  public void simpleInterpolation() {
    String template = "Hi, ${name}!";
    Map<String, Object> context = Map.of("name", "jack");

    NotificationTemplateInterpolator interpolator =
        new NotificationTemplateInterpolator(template, context);
    String result = interpolator.interpolate();

    assertEquals("Hi, jack!", result);
  }

  @Test
  public void nestedInterpolation() {
    String template = "User email: ${user.email}, Tenant ID: ${tenant.id}";
    Map<String, Object> user = Map.of("email", "koba@example.com");
    Map<String, Object> tenant = Map.of("id", "tenant-123");
    Map<String, Object> context = Map.of("user", user, "tenant", tenant);

    NotificationTemplateInterpolator interpolator =
        new NotificationTemplateInterpolator(template, context);
    String result = interpolator.interpolate();

    assertEquals("User email: koba@example.com, Tenant ID: tenant-123", result);
  }

  @Test
  public void missingValueInterpolation() {
    String template = "Hi, ${name}. Your age is ${age}";
    Map<String, Object> context = Map.of("name", "jack");

    NotificationTemplateInterpolator interpolator =
        new NotificationTemplateInterpolator(template, context);
    String result = interpolator.interpolate();

    assertEquals("Hi, jack. Your age is ", result);
  }

  @Test
  public void multiplePlaceholders() {
    String template = "Client ID: ${client.id}, Client Name: ${client.name}";
    Map<String, Object> client = Map.of("id", "client-456", "name", "Awesome App");
    Map<String, Object> context = Map.of("client", client);

    NotificationTemplateInterpolator interpolator =
        new NotificationTemplateInterpolator(template, context);
    String result = interpolator.interpolate();

    assertEquals("Client ID: client-456, Client Name: Awesome App", result);
  }

  @Test
  public void emptyTemplateReturnsEmptyString() {
    String template = "";
    Map<String, Object> context = new HashMap<>();

    NotificationTemplateInterpolator interpolator =
        new NotificationTemplateInterpolator(template, context);
    String result = interpolator.interpolate();

    assertEquals("", result);
  }
}
