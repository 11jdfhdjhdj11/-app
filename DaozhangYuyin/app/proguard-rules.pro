# sherpa-onnx 的配置类字段由 JNI 按名称读取，混淆后会找不到，必须原样保留
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keepclassmembers class com.k2fsa.sherpa.onnx.** { native <methods>; }
