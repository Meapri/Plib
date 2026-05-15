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
