cmake_minimum_required(VERSION 3.22.1)
project(androlinux_runtime_lab LANGUAGES CXX)

add_library(alr_runtime STATIC
    alr_runtime/alr_config.cpp
    alr_runtime/alr_path.cpp
    alr_runtime/alr_env.cpp
    alr_runtime/alr_hook.cpp
    alr_runtime/alr_interposer.cpp
)
add_library(alr_loader SHARED runtime_report.cpp runtime_plan.cpp)
add_library(alr_runtime_launcher SHARED alr_runtime_launcher.cpp runtime_plan.cpp)
add_library(alr_runtime_hook SHARED alr_runtime_hook.cpp)
add_library(alr_runtime_interposer SHARED alr_runtime_interposer.cpp)
add_executable(alr_test_command test_command.cpp)
add_executable(alr_proot_candidate proot_candidate.cpp)
set_target_properties(alr_test_command PROPERTIES OUTPUT_NAME "alr-test-command")
set_target_properties(alr_proot_candidate PROPERTIES OUTPUT_NAME "alr-proot-candidate")

target_compile_features(alr_runtime PRIVATE cxx_std_20)
target_compile_features(alr_loader PRIVATE cxx_std_20)
target_compile_features(alr_runtime_launcher PRIVATE cxx_std_20)
target_compile_features(alr_runtime_hook PRIVATE cxx_std_20)
target_compile_features(alr_runtime_interposer PRIVATE cxx_std_20)
target_compile_features(alr_test_command PRIVATE cxx_std_20)
target_compile_features(alr_proot_candidate PRIVATE cxx_std_20)

target_include_directories(alr_runtime PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(alr_loader PRIVATE alr_runtime log android EGL GLESv2)
target_link_libraries(alr_runtime_launcher PRIVATE alr_runtime)
target_link_libraries(alr_runtime_hook PRIVATE alr_runtime)
target_link_libraries(alr_runtime_interposer PRIVATE alr_runtime)
