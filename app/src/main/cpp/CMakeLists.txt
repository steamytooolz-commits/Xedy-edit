# CMakeLists.txt for compiling JNI native module proot
cmake_minimum_required(VERSION 3.18.1)

project("proot_module")

add_library(
        proot
        SHARED
        proot_bridge.cpp
)

find_library(
        log-lib
        log
)

target_link_libraries(
        proot
        ${log-lib}
)
