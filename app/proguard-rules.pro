# No reflection-based application components or advertising SDKs.
-keepattributes SourceFile,LineNumberTable

# Rhino JavaScript engine used by NewPipeExtractor
-keep class org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.**
-dontwarn org.mozilla.classfile.**
