# kotlinx.serialization ships its own consumer rules; keep our serializable DTOs explicitly as well.
-keep,includedescriptorclasses class app.necessityq.data.local.**$$serializer { *; }
-keepclassmembers class app.necessityq.data.local.** {
    *** Companion;
}
-keepclasseswithmembers class app.necessityq.data.local.** {
    kotlinx.serialization.KSerializer serializer(...);
}
