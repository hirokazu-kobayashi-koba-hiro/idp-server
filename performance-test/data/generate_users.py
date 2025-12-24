#!/usr/bin/env python3
"""
Performance Test User Data Generator

Generates user and authentication device data for performance testing.
Supports both single-tenant and multi-tenant configurations.

Usage:
    # Single tenant with 1M users (default tenant ID)
    python3 generate_users.py --users 1000000

    # Multi-tenant from configuration file
    python3 generate_users.py --users 100000 --tenants-file performance-test-tenant.json

    # First tenant with 1M users, rest with 100K (for both large-scale and multi-tenant tests)
    python3 generate_users.py --users 100000 --first-tenant-users 1000000 \
        --tenants-file performance-test-tenant.json

Output Files:
    - {prefix}_users.tsv          : User data for idp_user table
    - {prefix}_devices.tsv        : Device data for idp_user_authentication_devices table
    - {prefix}_test_users.json    : Test users for k6 CIBA tests (first 500 per tenant)
"""

import uuid
import os
import json
import argparse
from pathlib import Path


# Default configuration
DEFAULT_TENANT_ID = "67e7eae6-62b0-4500-9eff-87459f63fc66"
DEFAULT_CLIENT_ID = "clientSecretPost"
DEFAULT_CLIENT_SECRET = "clientSecretPostPassword1234567890123456789012345678901234567890123456789012345678901234567890"
OUTPUT_DIR = "./performance-test/data"
PROVIDER_ID = "idp-server"
EMAIL_DOMAIN = "example.com"
TEST_USERS_LIMIT = 500  # Number of test users per tenant for k6


def load_tenants(tenants_file: str) -> list:
    """Load tenant information from JSON file."""
    if not os.path.exists(tenants_file):
        print(f"Error: Tenant file not found: {tenants_file}")
        exit(1)

    with open(tenants_file, 'r') as f:
        tenants = json.load(f)

    print(f"Loaded {len(tenants)} tenants from {tenants_file}")
    return tenants


def create_single_tenant_config(tenant_id: str, client_id: str, client_secret: str) -> list:
    """Create a single tenant configuration."""
    return [{
        "tenantId": tenant_id,
        "clientId": client_id,
        "clientSecret": client_secret
    }]


def generate_users(tenants: list, users_per_tenant: int, first_tenant_users: int, output_prefix: str):
    """Generate user and device data for all tenants."""

    os.makedirs(OUTPUT_DIR, exist_ok=True)

    output_users = os.path.join(OUTPUT_DIR, f"{output_prefix}_users.tsv")
    output_devices = os.path.join(OUTPUT_DIR, f"{output_prefix}_devices.tsv")
    output_test_users = os.path.join(OUTPUT_DIR, f"{output_prefix}_test_users.json")

    # Calculate total users
    if len(tenants) == 1:
        total_users = first_tenant_users if first_tenant_users else users_per_tenant
    else:
        first_count = first_tenant_users if first_tenant_users else users_per_tenant
        rest_count = users_per_tenant * (len(tenants) - 1)
        total_users = first_count + rest_count

    print(f"Generating {total_users:,} users total")
    if first_tenant_users and len(tenants) > 1:
        print(f"  - First tenant: {first_tenant_users:,} users")
        print(f"  - Other {len(tenants) - 1} tenants: {users_per_tenant:,} users each")

    # Structure to hold test users for each tenant
    test_users_per_tenant = {tenant['tenantId']: [] for tenant in tenants}

    with open(output_users, "w") as f_users, \
         open(output_devices, "w") as f_devices:

        user_count = 0

        for tenant_idx, tenant in enumerate(tenants):
            tenant_id = tenant['tenantId']

            # Determine user count for this tenant
            if tenant_idx == 0 and first_tenant_users:
                tenant_user_count = first_tenant_users
            else:
                tenant_user_count = users_per_tenant

            print(f"\nTenant {tenant_idx + 1}/{len(tenants)}: {tenant_id} ({tenant_user_count:,} users)")

            for i in range(1, tenant_user_count + 1):
                user_sub = str(uuid.uuid4())
                device_id = str(uuid.uuid4())
                user_id = f"user_{uuid.uuid4()}"
                email = f"{user_id}@{EMAIL_DOMAIN}"
                phone = f"090{str(tenant_idx)}{str(i).zfill(7)}"

                # idp_user table data
                devices = [{
                    "id": device_id,
                    "os": "iOS 18.5",
                    "model": "iPhone15",
                    "platform": "iOS",
                    "priority": 1,
                    "notification_token": "test token",
                    "notification_channel": "fcm",
                    "preferred_for_notification": True
                }]
                devices_str = json.dumps(devices, ensure_ascii=False)
                devices_escaped = '"' + devices_str.replace('"', '""') + '"'

                user_line = (
                    f"{user_sub}\t{tenant_id}\t{PROVIDER_ID}\t{user_id}\t{user_id}\t"
                    f"{email}\ttrue\t{phone}\ttrue\t{email}\tIDENTITY_VERIFIED\t{devices_escaped}\n"
                )
                f_users.write(user_line)

                # idp_user_authentication_devices table data
                device_line = (
                    f"{device_id}\t{tenant_id}\t{user_sub}\tiOS 18.5\tiPhone15\tiOS\t\t"
                    f"Test App\t1\t[]\ttest token\tfcm\n"
                )
                f_devices.write(device_line)

                # Collect test users for k6 (first N per tenant)
                if i <= TEST_USERS_LIMIT:
                    test_users_per_tenant[tenant_id].append({
                        "device_id": device_id,
                        "user_id": user_sub,
                        "email": email,
                        "phone": phone,
                        "external_user_id": user_id,
                        "provider_id": PROVIDER_ID
                    })

                user_count += 1
                if user_count % 100000 == 0:
                    print(f"   Progress: {user_count:,} / {total_users:,}")

    print(f"\nUsers TSV: {output_users}")
    print(f"Devices TSV: {output_devices}")

    # Generate test users JSON for k6
    generate_test_users_json(tenants, test_users_per_tenant, output_test_users)

    return user_count


def generate_test_users_json(tenants: list, test_users_per_tenant: dict, output_file: str):
    """Generate JSON file with test users for k6 scripts."""

    output_data = []

    for tenant in tenants:
        tenant_id = tenant['tenantId']
        client_id = tenant.get('clientId', DEFAULT_CLIENT_ID)
        client_secret = tenant.get('clientSecret', DEFAULT_CLIENT_SECRET)

        output_data.append({
            "tenantId": tenant_id,
            "clientId": client_id,
            "clientSecret": client_secret,
            "users": test_users_per_tenant[tenant_id]
        })

    with open(output_file, 'w') as f:
        json.dump(output_data, f, indent=2)

    print(f"Test users JSON: {output_file}")


def main():
    parser = argparse.ArgumentParser(
        description='Generate user data for performance tests',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # Single tenant with 1M users (default tenant)
  python3 generate_users.py --users 1000000

  # Multi-tenant from config file (10 tenants x 100K users)
  python3 generate_users.py --users 100000 --tenants-file performance-test-tenant.json

  # First tenant 1M, rest 100K (supports both large-scale and multi-tenant tests)
  python3 generate_users.py --users 100000 --first-tenant-users 1000000 \\
      --tenants-file performance-test-tenant.json
        """
    )
    parser.add_argument(
        '--users',
        type=int,
        default=100000,
        help='Number of users per tenant (default: 100000)'
    )
    parser.add_argument(
        '--first-tenant-users',
        type=int,
        default=None,
        help='Number of users for the first tenant (overrides --users for first tenant)'
    )
    parser.add_argument(
        '--tenant-id',
        type=str,
        default=None,
        help=f'Single tenant ID (default: {DEFAULT_TENANT_ID})'
    )
    parser.add_argument(
        '--tenants-file',
        type=str,
        default=None,
        help='Path to tenant JSON file for multi-tenant setup'
    )
    parser.add_argument(
        '--client-id',
        type=str,
        default=DEFAULT_CLIENT_ID,
        help=f'Client ID for single tenant (default: {DEFAULT_CLIENT_ID})'
    )
    parser.add_argument(
        '--client-secret',
        type=str,
        default=DEFAULT_CLIENT_SECRET,
        help='Client secret for single tenant'
    )
    parser.add_argument(
        '--output-prefix',
        type=str,
        default=None,
        help='Output file prefix (auto-generated if not specified)'
    )

    args = parser.parse_args()

    print("=" * 60)
    print("Performance Test User Data Generator")
    print("=" * 60)

    # Determine tenant configuration
    if args.tenants_file:
        # Multi-tenant mode
        tenants = load_tenants(args.tenants_file)
        if not args.output_prefix:
            if args.first_tenant_users:
                first_str = f"{args.first_tenant_users // 1000000}m" if args.first_tenant_users >= 1000000 else f"{args.first_tenant_users // 1000}k"
                rest_str = f"{args.users // 1000}k"
                args.output_prefix = f"multi_tenant_{first_str}+{len(tenants)-1}x{rest_str}"
            else:
                args.output_prefix = f"multi_tenant_{len(tenants)}x{args.users // 1000}k"
    else:
        # Single-tenant mode
        tenant_id = args.tenant_id or DEFAULT_TENANT_ID
        tenants = create_single_tenant_config(tenant_id, args.client_id, args.client_secret)
        user_count = args.first_tenant_users or args.users
        if not args.output_prefix:
            user_count_str = f"{user_count // 1000000}m" if user_count >= 1000000 else f"{user_count // 1000}k"
            args.output_prefix = f"single_tenant_{user_count_str}"

    if len(tenants) == 0:
        print("Error: No tenants configured.")
        exit(1)

    # Generate users
    total = generate_users(tenants, args.users, args.first_tenant_users, args.output_prefix)

    print("\n" + "=" * 60)
    print(f"Generation complete!")
    print(f"   Total users: {total:,}")
    print(f"   Total devices: {total:,}")
    print("=" * 60)

    print("\nNext steps:")
    print(f"   ./performance-test/data/import_users.sh {args.output_prefix}")


if __name__ == "__main__":
    main()
