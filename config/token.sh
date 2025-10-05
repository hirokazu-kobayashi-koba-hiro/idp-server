curl -X POST "http://localhost:8080/3c6b4324-f3ee-4e1a-9453-be4af9958aa5/v1/tokens" \
-H "Content-Type: application/x-www-form-urlencoded" \
-d "grant_type=password" \
-d "username=test@example.com" \
-d "password=TestPassword123!" \
-d "client_id=0fd20332-0c64-4888-a254-a55145c6f364" \
-d "client_secret=test-org-secret-7b600d5e3f92cb339a3461389002a3c9" \
-d "scope=openid profile email management"