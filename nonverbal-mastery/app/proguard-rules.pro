# kotlinx.serialization and Navigation ship their own consumer rules.
# Content models are decoded reflectively by generated serializers; keep their companions.
-keepclassmembers class com.nonverbal.mastery.data.models.** {
    *** Companion;
}
-keepclasseswithmembers class com.nonverbal.mastery.data.models.** {
    kotlinx.serialization.KSerializer serializer(...);
}
