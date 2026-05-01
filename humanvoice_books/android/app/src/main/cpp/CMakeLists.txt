cmake_minimum_required(VERSION 3.22.1)
project(humanvoice_native LANGUAGES CXX)

# We do NOT rebuild sherpa-onnx from source — the prebuilt
# libsherpa-onnx-jni.so is delivered by the sherpa_onnx Flutter plugin AAR.
# This stub library exists only to satisfy Gradle's externalNativeBuild
# wiring and to give us a place to drop future custom C++ helpers.
add_library(humanvoice_native SHARED stub.cpp)

find_library(log-lib log)
target_link_libraries(humanvoice_native ${log-lib})
