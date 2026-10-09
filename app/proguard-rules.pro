# Keep kotlinx.serialization metadata for the saved data classes.
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.vladk.financeplan.data.**$$serializer { *; }
-keepclassmembers class com.vladk.financeplan.data.** {
    *** Companion;
}
