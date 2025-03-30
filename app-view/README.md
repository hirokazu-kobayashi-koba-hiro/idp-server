## build

```shell
npm run build-and-copy
```

## debug

prompt=login
```shell
curl -v "http://localhost:8080/67e7eae6-62b0-4500-9eff-87459f63fc66/v1/authorizations?prompt=login&scope=openid+profile+phone+emailaccount+transfers&response_type=code&client_id=clientSecretPost&redirect_uri=https%3A%2F%2Fwww.certification.openid.net%2Ftest%2Fa%2Fidp_oidc_basic%2Fcallback&state=aiueo&organization_id=123&organization_name=test"
```

prompt=none
```url
    http://localhost:8080/67e7eae6-62b0-4500-9eff-87459f63fc66/v1/authorizations?prompt=none&scope=openid+profile+phone+emailaccount+transfers&response_type=code&client_id=clientSecretPost&redirect_uri=https%3A%2F%2Fwww.certification.openid.net%2Ftest%2Fa%2Fidp_oidc_basic%2Fcallback&state=aiueo&organization_id=123&organization_name=test
```

```shell
https://www.certification.openid.net/test/a/idp_oidc_basic/callback?
error_description=authorization+request+contains+unauthorized+userinfo+claims+%28website+zoneinfo+birthdate+gender+profile+phone_number_verified+preferred_username+given_name+middle_name+locale+picture+updated_at+name+nickname+phone_number+family_name%29
&iss=http%3A%2F%2Flocalhost%3A8080%2F67e7eae6-62b0-4500-9eff-87459f63fc66&state=aiueo&error=interaction_required
```

```shell
export AUTH_CODE=0RqhvitecA3C4vifE4dB8RDD2KM
curl -v -X POST "http://localhost:8080/67e7eae6-62b0-4500-9eff-87459f63fc66/api/v1/tokens" \
-d "client_id=clientSecretPost" \
-d "client_secret=clientSecretPostPassword1234567890123456789012345678901234567890123456789012345678901234567890" \
-d "grant_type=authorization_code" \
-d "redirect_uri=https%3A%2F%2Fwww.certification.openid.net%2Ftest%2Fa%2Fidp_oidc_basic%2Fcallback" \
-d "code=${AUTH_CODE}"

```