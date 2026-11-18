# SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
#
# SPDX-License-Identifier: AGPL-3.0-or-later

# Taafeit ProGuard rules

# The AIDL stub and the Shizuku user service are instantiated by reflection
# from the Shizuku daemon, so release minification must not touch them.
-keep class taafeit.app.IShizukuService { *; }
-keep class taafeit.app.IShizukuService$* { *; }
-keep class taafeit.app.ShizukuService { *; }
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }

-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*

# Binder subclasses need their constructors for reflective instantiation
-keepclassmembers class * extends android.os.Binder {
    public <init>(...);
}
