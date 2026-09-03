cmake_minimum_required(VERSION 3.22.1)
project(aicli_pty C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

add_library(aicli_pty SHARED
    pty_native.c
)

target_compile_options(aicli_pty PRIVATE -Wall -Wextra -Wno-unused-parameter)

find_library(log-lib log)

target_link_libraries(aicli_pty
    ${log-lib}
)
