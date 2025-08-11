/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.notification.push.fcm;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.idp.server.authentication.interactors.device.AuthenticationDeviceNotifier;
import org.idp.server.core.oidc.authentication.config.AuthenticationConfiguration;
import org.idp.server.core.oidc.identity.device.AuthenticationDevice;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.notification.NotificationChannel;
import org.idp.server.platform.notification.NotificationTemplate;

public class FcmNotifier implements AuthenticationDeviceNotifier {

  LoggerWrapper log = LoggerWrapper.getLogger(FcmNotifier.class);
  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
  Map<String, FirebaseMessaging> cache = new ConcurrentHashMap<>();

  @Override
  public NotificationChannel chanel() {
    return new NotificationChannel("fcm");
  }

  @Override
  public void notify(
      Tenant tenant, AuthenticationDevice device, AuthenticationConfiguration configuration) {

    try {
      log.debug("Fcm notification channel called");

      if (!device.hasNotificationToken()) {
        log.debug("Device has no notification token");
        return;
      }

      // TODO
      FcmConfiguration fcmConfiguration = new FcmConfiguration();
      FirebaseMessaging firebaseMessaging = getOrInitFirebaseMessaging(tenant, fcmConfiguration);

      NotificationTemplate notificationTemplate = fcmConfiguration.findTemplate("default");
      String notificationToken = device.notificationToken().value();

      Message message =
          Message.builder()
              .setToken(notificationToken)
              .setAndroidConfig(
                  AndroidConfig.builder()
                      .setPriority(AndroidConfig.Priority.HIGH)
                      .putData("sender", notificationTemplate.optSender(tenant.identifierValue()))
                      .putData("title", notificationTemplate.optTitle("Transaction Authentication"))
                      .putData(
                          "body",
                          notificationTemplate.optBody(
                              "Please approve the transaction to continue."))
                      .build())
              .setApnsConfig(
                  ApnsConfig.builder()
                      .putHeader("apns-priority", "10")
                      .putCustomData(
                          "sender", notificationTemplate.optSender(tenant.identifierValue()))
                      .putCustomData(
                          "title", notificationTemplate.optTitle("Transaction Authentication"))
                      .putCustomData(
                          "body",
                          notificationTemplate.optBody(
                              "Please approve the transaction to continue."))
                      .setAps(Aps.builder().setContentAvailable(true).build())
                      .build())
              .build();

      String result = firebaseMessaging.send(message);

      log.info("fcm result: {}, tenant: {}", result, tenant.identifierValue());
    } catch (Exception e) {

      log.error("Fcm is failed: {}, tenant: {}", e.getMessage(), tenant.identifierValue());
    }
  }

  FirebaseMessaging getOrInitFirebaseMessaging(Tenant tenant, FcmConfiguration fcmConfiguration) {

    return cache.computeIfAbsent(
        tenant.identifierValue(),
        (key) -> {
          try {
            String credential = fcmConfiguration.credential();
            FirebaseOptions options =
                FirebaseOptions.builder()
                    .setCredentials(
                        GoogleCredentials.fromStream(
                            new ByteArrayInputStream(credential.getBytes())))
                    .build();

            FirebaseApp firebaseApp = FirebaseApp.initializeApp(options, tenant.identifierValue());
            return FirebaseMessaging.getInstance(firebaseApp);

          } catch (IOException e) {
            throw new FcmRuntimeException(e);
          }
        });
  }
}
