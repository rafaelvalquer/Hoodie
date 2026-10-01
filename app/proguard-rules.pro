# Room/Hilt ship their own consumer rules. Keep enum names used as persisted values.
-keepclassmembers enum com.hoodie.app.** { *; }

# SQLCipher: classes chamadas via JNI
-keep class net.zetetic.database.** { *; }
-keep class net.zetetic.database.sqlcipher.** { *; }
