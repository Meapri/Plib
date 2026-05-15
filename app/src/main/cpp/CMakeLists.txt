cmake_minimum_required(VERSION 3.22.1)
project(androlinux_runtime_lab LANGUAGES CXX)

add_library(alr_loader SHARED runtime_report.cpp)

target_compile_features(alr_loader PRIVATE cxx_std_20)

target_link_libraries(alr_loader PRIVATE log)
