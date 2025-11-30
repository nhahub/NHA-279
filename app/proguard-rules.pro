# Enhanced ProGuard Rules for MovieApp - Last Updated: 2024
# Based on dependencies: Compose BOM 2024.05.00, Firebase BOM 33.7.0, Moshi, Retrofit, WorkManager, etc.
# For more details, see http://developer.android.com/guide/developing/tools/proguard.html

# ================================
# APP-SPECIFIC RULES
# ================================

# App-specific rules for data classes and models to prevent issues with Moshi serialization
# Targeted keeps added only if specific classes fail during release testing

# ================================
# DATA PERSISTENCE
# ================================

# DataStore rules - Keep only essential classes for local persistence
-keep class androidx.datastore.preferences.** { *; }
-keep class androidx.datastore.core.** { *; }
-dontwarn androidx.datastore.preferences.**
-dontwarn androidx.datastore.core.**

# ================================
# THIRD-PARTY LIBRARIES
# ================================

# YouTube Player library rules - Keep only essential classes
-keep class com.pierfrancescosoffritti.androidyoutubeplayer.** { *; }
-keep class com.pierfrancescosoffritti.androidyoutubeplayer.core.player.** { *; }
-dontwarn com.pierfrancescosoffritti.androidyoutubeplayer.**

# Coil rules - Keep only essential classes
-keep class coil.request.** { *; }
-keep class coil.memory.** { *; }
-keep class coil.** { *; }
-keep interface coil.** { *; }
-dontwarn coil.**

# ================================
# NETWORKING (Retrofit, Moshi, OkHttp)
# ================================

# Retrofit rules
# Keep Continuation for Suspend Functions (CRITICAL for Retrofit + Coroutines)
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# Keep Retrofit Call and Response with Optimization
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# Keep Retrofit Service Interfaces
-if interface com.trailerly.network.** { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# Moshi rules
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @com.squareup.moshi.* <methods>;
}

-keep @com.squareup.moshi.JsonQualifier interface *

# Keep Moshi Kotlin Adapter Classes
-keep class com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory { *; }
-keep class com.squareup.moshi.kotlin.reflect.** { *; }

# Keep @FromJson/@ToJson Methods
-keepclassmembers class * { @com.squareup.moshi.FromJson <methods>; @com.squareup.moshi.ToJson <methods>; }

# Keep Enum Members for Moshi
-keepclassmembers enum * { <fields>; public static **[] values(); }

# OkHttp platform used only on JVM and when Conscrypt and other security providers are available.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# A resource is loaded with a relative path so the package of this class must be preserved.
-adaptresourcefilenames okhttp3/internal/publicsuffix/PublicSuffixDatabase.gz

# ================================
# KOTLIN & COROUTINES
# ================================

# Kotlin rules - Keep only essential metadata
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# Coroutines rules - Keep only essential classes
-keep class kotlinx.coroutines.flow.** { *; }
-keep class kotlinx.coroutines.channels.** { *; }
-keep class kotlinx.coroutines.flow.StateFlow { *; }
-keep class kotlinx.coroutines.flow.SharedFlow { *; }
-keep class kotlinx.coroutines.flow.MutableStateFlow { *; }
-keep class kotlinx.coroutines.flow.MutableSharedFlow { *; }
-dontwarn kotlinx.coroutines.**

# Keep Sealed Classes Structure (CRITICAL for UiState, AuthState, AuthResult)
-keep class com.trailerly.uistate.UiState { *; }
-keep class com.trailerly.uistate.UiState$* { *; }
-keep class com.trailerly.auth.AuthState { *; }
-keep class com.trailerly.auth.AuthState$* { *; }
-keep class com.trailerly.auth.AuthResult { *; }
-keep class com.trailerly.auth.AuthResult$* { *; }

# ================================
# JETPACK COMPOSE
# ================================

# Compose rules - Keep only essential annotations
-keep @androidx.compose.runtime.Composable class * { *; }
-keep @androidx.compose.runtime.Stable class * { *; }
-keep @androidx.compose.runtime.Immutable class * { *; }
-keep class androidx.navigation.NavHost { *; }
-keep class androidx.navigation.NavController { *; }
-keep @kotlin.RequiresOptIn class * { *; }
-keep @androidx.compose.material3.ExperimentalMaterial3Api class * { *; }
-dontwarn androidx.compose.**
-dontwarn androidx.compose.material3.**

# ================================
# FIREBASE & GOOGLE PLAY SERVICES
# ================================

# Firebase rules - Keep only essential classes
-keep class com.google.firebase.auth.FirebaseAuth { *; }
-keep class com.google.firebase.auth.FirebaseUser { *; }
-keep class com.google.android.gms.auth.api.signin.GoogleSignInAccount { *; }
-keep class com.google.android.gms.auth.api.signin.GoogleSignInOptions { *; }

# Keep Firebase Core
-keep class com.google.firebase.FirebaseApp { *; }
-keep class com.google.firebase.FirebaseOptions { *; }

# Keep GoogleID Library
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn com.google.android.libraries.identity.googleid.**

# ================================
# WORKMANAGER
# ================================

# Keep Worker Classes
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }
-keep public class * extends androidx.work.ListenableWorker { public <init>(...); }

# Keep Specific Worker
-keep class com.trailerly.worker.MovieReleaseWorker { *; }

# Keep WorkManager Parameters
-keep class androidx.work.WorkerParameters { *; }
-keep class androidx.work.InputMerger { *; }

# ================================
# GENERAL CONFIGURATION
# ================================

# Keep attributes consolidated
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault, Exceptions, SourceFile, LineNumberTable

# Optimize Unused Code Removal (uncomment to strip debug logs)
#-assumenosideeffects class android.util.Log { public static *** d(...); public static *** v(...); }

# R8 Full Mode Compatibility (uncomment for debugging)
#-dontoptimize
#-dontobfuscate

# ================================
# TESTING RELEASE BUILDS
# ================================
# 1. Build release APK: ./gradlew assembleRelease
# 2. Check R8 output: app/build/outputs/mapping/release/
#    - mapping.txt: Shows obfuscation mappings
#    - missing_rules.txt: Shows missing keep rules (if any)
#    - usage.txt: Shows removed code
# 3. Test critical flows:
#    - API calls (movie fetching, search)
#    - Authentication (sign in, sign out)
#    - Saved movies (DataStore persistence)
#    - Notifications (WorkManager)
#    - Navigation between screens
# 4. Monitor Logcat for ClassNotFoundException or NoSuchMethodException
# 5. If issues occur, check missing_rules.txt and add targeted keeps