# control plane schema

## authorization-server

```shell
cat ../../libs/idp-server-core/src/main/resources/schema/1.0/authorization-server.json | yq -P > schema.yaml
```

## clientAttributes

```shell
cat ../../libs/idp-server-core/src/main/resources/schema/1.0/clientAttributes.json | yq -P > schema.yaml
```

## authentication

```shell
cat ../../libs/idp-server-authentication-interactors/src/main/resources/schema/1.0/authentication/authentication-config.json | yq -P > schema.yaml
```

### initial-registration

```shell
cat ../../libs/idp-server-authentication-interactors/src/main/resources/schema/1.0/authentication/initial-registration/standard.json | yq -P > schema.yaml
```

### sms external

```shell
cat ../../libs/idp-server-authentication-interactors/src/main/resources/schema/1.0/authentication/sms/external-authn.json | yq -P > schema.yaml
```

### email smtp

```shell
cat ../../libs/idp-server-authentication-interactors/src/main/resources/schema/1.0/authentication/email/smtp.json | yq -P > schema.yaml
```

### fido-uaf external

```shell
cat ../../libs/idp-server-authentication-interactors/src/main/resources/schema/1.0/authentication/fido-uaf/external-authn.json | yq -P > schema.yaml
```

### webauthn4j

```shell
cat ../../libs/idp-server-authentication-interactors/src/main/resources/schema/1.0/authentication/webauthn/webauthn4j.json | yq -P > schema.yaml
```

### legacy-authn

```shell
cat ../../libs/idp-server-authentication-interactors/src/main/resources/schema/1.0/authentication/legacy/standard.json | yq -P > schema.yaml
```

### authentication-device

```shell
cat ../../libs/idp-server-authentication-interactors/src/main/resources/schema/1.0/authentication/authentication-device/fcm.json | yq -P > schema.yaml
```