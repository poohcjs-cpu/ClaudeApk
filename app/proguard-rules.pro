# kotlinx.serialization: @Serializable 클래스의 직렬화기 유지
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class **$$serializer { *; }
-dontwarn org.slf4j.**
