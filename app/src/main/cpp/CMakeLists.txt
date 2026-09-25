cmake_minimum_required(VERSION 3.22.1)
project(ypower_native_trace)

find_package(bytehook REQUIRED CONFIG)

add_library(
        ypower_native_trace
        SHARED
        ypower_native_trace.cpp
)

find_library(log-lib log)
find_library(dl-lib dl)

target_link_libraries(
        ypower_native_trace
        bytehook::bytehook
        ${log-lib}
        ${dl-lib}
)
