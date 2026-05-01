// Placeholder native library. sherpa-onnx ships its own prebuilt JNI;
// Kokoro inference and Qwen LLM calls go through the Flutter plugin
// (sherpa_onnx) which dlopen's libsherpa-onnx-jni.so at runtime.
extern "C" int humanvoice_native_alive() { return 1; }
