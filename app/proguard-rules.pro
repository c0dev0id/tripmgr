# Google Drive API
-keep class com.google.api.services.drive.** { *; }
-keep class com.google.api.client.** { *; }
-dontwarn com.google.api.client.**

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.tripmgr.data.model.** { *; }
