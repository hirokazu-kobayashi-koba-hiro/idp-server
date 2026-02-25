#!/bin/bash

# Run All Use Case Example Tests
#
# Executes setup → verify → delete for each use case example.
# Reports per-use-case and overall results.
#
# Usage:
#   ./run-all-tests.sh                      # Run all use cases
#   ./run-all-tests.sh login-password-only   # Run specific use case
#   ./run-all-tests.sh login-social ekyc     # Run multiple specific use cases

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

ALL_USE_CASES=(
  login-password-only
  login-social
  mfa-email
  passwordless-fido2
  ekyc
  third-party
)

# Parse arguments: use specified use cases or all
if [ $# -gt 0 ]; then
  USE_CASES=("$@")
else
  USE_CASES=("${ALL_USE_CASES[@]}")
fi

# Validate use case names
for UC in "${USE_CASES[@]}"; do
  if [ ! -d "${SCRIPT_DIR}/${UC}" ]; then
    echo "Error: Use case directory not found: ${SCRIPT_DIR}/${UC}"
    echo "Available use cases: ${ALL_USE_CASES[*]}"
    exit 1
  fi
  if [ ! -f "${SCRIPT_DIR}/${UC}/verify.sh" ]; then
    echo "Error: verify.sh not found for use case: ${UC}"
    exit 1
  fi
done

echo "############################################"
echo "# Use Case Example Tests"
echo "############################################"
echo ""
echo "Use cases to test: ${USE_CASES[*]}"
echo ""

TOTAL_USE_CASES=${#USE_CASES[@]}
PASSED_USE_CASES=0
FAILED_USE_CASES=0
FAILED_NAMES=()

for UC in "${USE_CASES[@]}"; do
  echo ""
  echo "============================================"
  echo "  USE CASE: ${UC}"
  echo "============================================"
  echo ""

  UC_DIR="${SCRIPT_DIR}/${UC}"
  UC_RESULT="PASS"

  # --- Setup ---
  echo "--- Setup ---"
  if bash "${UC_DIR}/setup.sh"; then
    echo ""
    echo "Setup: OK"
  else
    echo ""
    echo "Setup: FAILED"
    UC_RESULT="FAIL"
  fi
  echo ""

  # --- Verify ---
  if [ "${UC_RESULT}" = "PASS" ]; then
    echo "--- Verify ---"
    if bash "${UC_DIR}/verify.sh"; then
      echo ""
      echo "Verify: OK"
    else
      echo ""
      echo "Verify: FAILED"
      UC_RESULT="FAIL"
    fi
    echo ""
  fi

  # --- Delete (always attempt cleanup) ---
  echo "--- Delete ---"
  if bash "${UC_DIR}/delete.sh"; then
    echo ""
    echo "Delete: OK"
  else
    echo ""
    echo "Delete: FAILED (cleanup issue, not counted as test failure)"
  fi
  echo ""

  # --- Record result ---
  if [ "${UC_RESULT}" = "PASS" ]; then
    echo ">>> ${UC}: PASSED"
    PASSED_USE_CASES=$((PASSED_USE_CASES + 1))
  else
    echo ">>> ${UC}: FAILED"
    FAILED_USE_CASES=$((FAILED_USE_CASES + 1))
    FAILED_NAMES+=("${UC}")
  fi
done

# ============================================================
# Overall Summary
# ============================================================
echo ""
echo "############################################"
echo "# Overall Results"
echo "############################################"
echo ""
echo "  Total:  ${TOTAL_USE_CASES}"
echo "  Passed: ${PASSED_USE_CASES}"
echo "  Failed: ${FAILED_USE_CASES}"
echo ""

if [ ${FAILED_USE_CASES} -gt 0 ]; then
  echo "  Failed use cases:"
  for NAME in "${FAILED_NAMES[@]}"; do
    echo "    - ${NAME}"
  done
  echo ""
  echo "Some use cases failed. Review the output above for details."
  exit 1
else
  echo "All use cases passed!"
  exit 0
fi
