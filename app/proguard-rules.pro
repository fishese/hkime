# ProGuard rules for DualQuickIME

# Keep the InputMethodService and related classes
-keep class com.awcjack.dualquickime.DualQuickInputMethodService { *; }

# Keep UI classes (important for IME views)
-keep class com.awcjack.dualquickime.ui.** { *; }

# Keep theme classes
-keep class com.awcjack.dualquickime.theme.** { *; }

# Keep data classes and their constructors
-keep class com.awcjack.dualquickime.data.** { *; }
-keepclassmembers class com.awcjack.dualquickime.data.** {
    <init>(...);
}

# Keep Settings Activity
-keep class com.awcjack.dualquickime.SettingsActivity { *; }

# Keep util classes
-keep class com.awcjack.dualquickime.util.** { *; }

# Android framework
-keep public class * extends android.inputmethodservice.InputMethodService
-keep public class * extends android.app.Activity
-keep public class * extends android.view.View

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep custom view constructors
-keepclasseswithmembers class * {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Keep onClick handlers
-keepclassmembers class * {
    public void onClick(android.view.View);
}

# OpenCC - Chinese character conversion
-keep class openccjava.** { *; }

# Keep enum values
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# AndroidX Security / EncryptedSharedPreferences
-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
