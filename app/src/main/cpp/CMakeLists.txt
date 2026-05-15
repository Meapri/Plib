cmake_minimum_required(VERSION 3.22.1)
project(androlinux_runtime_lab LANGUAGES CXX)

add_library(alr_loader SHARED runtime_report.cpp runtime_plan.cpp)
add_executable(alr_test_command test_command.cpp)
add_executable(alr_proot_candidate proot_candidate.cpp)
set_target_properties(alr_test_command PROPERTIES OUTPUT_NAME "alr-test-command")
set_target_properties(alr_proot_candidate PROPERTIES OUTPUT_NAME "alr-proot-candidate")

target_compile_features(alr_loader PRIVATE cxx_std_20)
target_compile_features(alr_test_command PRIVATE cxx_std_20)
target_compile_features(alr_proot_candidate PRIVATE cxx_std_20)

target_link_libraries(alr_loader PRIVATE log android EGL GLESv2)
