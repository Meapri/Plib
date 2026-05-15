#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

cxx="${CXX:-g++}"
"$cxx" -std=c++20 -Wall -Wextra -Werror \
  -Iapp/src/main/cpp \
  tests/native_runtime_plan_test.cpp \
  app/src/main/cpp/alr_runtime/alr_path.cpp \
  app/src/main/cpp/alr_runtime/alr_config.cpp \
  app/src/main/cpp/alr_runtime/alr_entry.cpp \
  app/src/main/cpp/alr_runtime/alr_elf.cpp \
  app/src/main/cpp/alr_runtime/alr_exec.cpp \
  app/src/main/cpp/alr_runtime/alr_handoff.cpp \
  app/src/main/cpp/alr_runtime/alr_image.cpp \
  app/src/main/cpp/alr_runtime/alr_launch.cpp \
  app/src/main/cpp/alr_runtime/alr_trampoline.cpp \
  app/src/main/cpp/alr_runtime/alr_transfer.cpp \
  app/src/main/cpp/runtime_plan.cpp \
  -o /tmp/alr-native-runtime-plan-test

/tmp/alr-native-runtime-plan-test

"$cxx" -std=c++20 -Wall -Wextra -Werror \
  -Iapp/src/main/cpp \
  tests/native_backend_policy_test.cpp \
  app/src/main/cpp/alr_runtime/alr_path.cpp \
  app/src/main/cpp/alr_runtime/alr_config.cpp \
  app/src/main/cpp/alr_runtime/alr_entry.cpp \
  app/src/main/cpp/alr_runtime/alr_elf.cpp \
  app/src/main/cpp/alr_runtime/alr_exec.cpp \
  app/src/main/cpp/alr_runtime/alr_handoff.cpp \
  app/src/main/cpp/alr_runtime/alr_image.cpp \
  app/src/main/cpp/alr_runtime/alr_launch.cpp \
  app/src/main/cpp/alr_runtime/alr_trampoline.cpp \
  app/src/main/cpp/alr_runtime/alr_transfer.cpp \
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
  tests/native_alr_runtime_config_test.cpp \
  app/src/main/cpp/alr_runtime/alr_path.cpp \
  app/src/main/cpp/alr_runtime/alr_config.cpp \
  -o /tmp/alr-native-runtime-config-test

/tmp/alr-native-runtime-config-test

"$cxx" -std=c++20 -Wall -Wextra -Werror \
  -Iapp/src/main/cpp \
  tests/native_alr_runtime_elf_test.cpp \
  app/src/main/cpp/alr_runtime/alr_elf.cpp \
  app/src/main/cpp/alr_runtime/alr_image.cpp \
  -o /tmp/alr-native-runtime-elf-test

/tmp/alr-native-runtime-elf-test

"$cxx" -std=c++20 -Wall -Wextra -Werror \
  -Iapp/src/main/cpp \
  tests/native_alr_runtime_image_test.cpp \
  app/src/main/cpp/alr_runtime/alr_elf.cpp \
  app/src/main/cpp/alr_runtime/alr_image.cpp \
  -o /tmp/alr-native-runtime-image-test

/tmp/alr-native-runtime-image-test

"$cxx" -std=c++20 -Wall -Wextra -Werror \
  -Iapp/src/main/cpp \
  tests/native_alr_runtime_trampoline_test.cpp \
  app/src/main/cpp/alr_runtime/alr_path.cpp \
  app/src/main/cpp/alr_runtime/alr_config.cpp \
  app/src/main/cpp/alr_runtime/alr_entry.cpp \
  app/src/main/cpp/alr_runtime/alr_elf.cpp \
  app/src/main/cpp/alr_runtime/alr_exec.cpp \
  app/src/main/cpp/alr_runtime/alr_handoff.cpp \
  app/src/main/cpp/alr_runtime/alr_image.cpp \
  app/src/main/cpp/alr_runtime/alr_launch.cpp \
  app/src/main/cpp/alr_runtime/alr_trampoline.cpp \
  app/src/main/cpp/alr_runtime/alr_transfer.cpp \
  -o /tmp/alr-native-runtime-trampoline-test

/tmp/alr-native-runtime-trampoline-test

"$cxx" -std=c++20 -Wall -Wextra -Werror \
  -Iapp/src/main/cpp \
  tests/native_alr_runtime_exec_test.cpp \
  app/src/main/cpp/alr_runtime/alr_path.cpp \
  app/src/main/cpp/alr_runtime/alr_exec.cpp \
  -o /tmp/alr-native-runtime-exec-test

/tmp/alr-native-runtime-exec-test

"$cxx" -std=c++20 -Wall -Wextra -Werror \
  -Iapp/src/main/cpp \
  tests/native_alr_runtime_launch_test.cpp \
  app/src/main/cpp/alr_runtime/alr_path.cpp \
  app/src/main/cpp/alr_runtime/alr_config.cpp \
  app/src/main/cpp/alr_runtime/alr_entry.cpp \
  app/src/main/cpp/alr_runtime/alr_elf.cpp \
  app/src/main/cpp/alr_runtime/alr_exec.cpp \
  app/src/main/cpp/alr_runtime/alr_handoff.cpp \
  app/src/main/cpp/alr_runtime/alr_image.cpp \
  app/src/main/cpp/alr_runtime/alr_launch.cpp \
  app/src/main/cpp/alr_runtime/alr_trampoline.cpp \
  app/src/main/cpp/alr_runtime/alr_transfer.cpp \
  -o /tmp/alr-native-runtime-launch-test

/tmp/alr-native-runtime-launch-test

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
