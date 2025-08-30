# SPDX-FileCopyrightText: 2015 - 2024 Rime community
#
# SPDX-License-Identifier: GPL-3.0-or-later

# find_package(Opencc REQUIRED)

set(RIME_JNI_SOURCES
  key.cc
  levers.cc
  proto.cc
  rime_jni.cc
)
add_library(rime_jni SHARED ${RIME_JNI_SOURCES})
target_link_libraries(rime_jni rime-static)
target_include_directories(rime_jni PRIVATE
  "${CMAKE_BINARY_DIR}/librime/src"
  "${CMAKE_SOURCE_DIR}/librime/src"
)
