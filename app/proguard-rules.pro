# Room generates implementation classes and persists entities through generated
# adapters; retain their annotations and generated database entry points.
-keep class **_Impl { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# Kotlin serialization and Supabase models are discovered by serializers.
-keepclassmembers class **$$serializer { *; }
-keepclassmembers class **$Companion { *; }

# Android discovers the opt-in accessibility service from the manifest.
-keep class com.faheem.habits.features.screen_time.InstagramScrollCounterService { *; }

# Vault crypto entry points must remain available to the app.
-keep class com.faheem.habits.features.vault.VaultCrypto { *; }