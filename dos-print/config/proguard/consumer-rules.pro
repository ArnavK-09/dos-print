# Dos Print release consumer rules.
# The app has no reflection-based model layer, so the defaults from
# proguard-android-optimize.txt are sufficient. Entries below only pin
# framework classes that R8 must not rename because they are resolved by name.

-keepnames class com.dosprint.data.model.** { *; }

# kotlinx.serialization keeps generated serializers reachable through the
# @Serializable companion. R8 rules shipped with the library cover the runtime;
# these entries keep enum names stable for the persisted preference files.
-keepclassmembers enum com.dosprint.data.model.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Compose runtime uses these entry points through reflection in tooling only.
-dontwarn org.jetbrains.annotations.**