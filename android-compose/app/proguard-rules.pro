# Keep model classes used by Retrofit/serialization
-keepattributes *Annotation*, InnerClasses
-keep class com.spotifymashup.generator.data.** { *; }
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <init>(...);
}
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
