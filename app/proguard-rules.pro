# BouncyCastle Argon2id / SCrypt classes used directly by Solina
-keep class org.bouncycastle.crypto.generators.Argon2BytesGenerator { *; }
-keep class org.bouncycastle.crypto.generators.SCrypt { *; }
-keep class org.bouncycastle.crypto.params.Argon2Parameters** { *; }
-dontwarn org.bouncycastle.**

# Strip any accidental debug logging from third-party libraries in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
