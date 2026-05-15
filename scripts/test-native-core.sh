#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

cxx="${CXX:-g++}"
"$cxx" -std=c++20 -Wall -Wextra -Werror \
  -Iapp/src/main/cpp \
  tests/native_runtime_plan_test.cpp \
  app/src/main/cpp/runtime_plan.cpp \
  -o /tmp/alr-native-runtime-plan-test

/tmp/alr-native-runtime-plan-test

"$cxx" -std=c++20 -Wall -Wextra -Werror \
  -Iapp/src/main/cpp \
  tests/native_backend_policy_test.cpp \
  app/src/main/cpp/runtime_plan.cpp \
  -o /tmp/alr-native-backend-policy-test

/tmp/alr-native-backend-policy-test

"$cxx" -std=c++20 -Wall -Wextra -Werror \
  -Iapp/src/main/cpp \
  tests/native_alr_runtime_path_test.cpp \
  app/src/main/cpp/alr_runtime/alr_path.cpp \
  app/src/main/cpp/alr_runtime/alr_env.cpp \
  -o /tmp/alr-native-runtime-path-test

/tmp/alr-native-runtime-path-test

"$cxx" -std=c++20 -Wall -Wextra -Werror \
  -Iapp/src/main/cpp \
  tests/native_alr_runtime_hook_test.cpp \
  app/src/main/cpp/alr_runtime/alr_path.cpp \
  app/src/main/cpp/alr_runtime/alr_hook.cpp \
  -o /tmp/alr-native-runtime-hook-test

/tmp/alr-native-runtime-hook-test

"$cxx" -std=c++20 -Wall -Wextra -Werror \
  -Iapp/src/main/cpp \
  tests/native_alr_runtime_interposer_test.cpp \
  app/src/main/cpp/alr_runtime/alr_path.cpp \
  app/src/main/cpp/alr_runtime/alr_interposer.cpp \
  -o /tmp/alr-native-runtime-interposer-test

/tmp/alr-native-runtime-interposer-test
