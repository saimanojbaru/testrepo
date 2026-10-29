# Sortora R8 rules.
# Most libraries (ML Kit, Room, Hilt, kotlinx.serialization, WorkManager, Coil) ship their own
# consumer rules. Only app-specific rules belong here.

# Analysis results are stored as JSON via kotlinx.serialization; keep generated serializers
# for our own models (the library's consumer rules cover the common cases, this is belt-and-braces).
-keepclassmembers @kotlinx.serialization.Serializable class com.sortora.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# Strip verbose/debug logging from release builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
