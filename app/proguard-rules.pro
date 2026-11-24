# Keep Kotlin metadata and Compose runtime entry points stable under R8.
-keep class kotlin.Metadata { *; }
-dontwarn org.jetbrains.annotations.**
