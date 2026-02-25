# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-keep class ialorabi.ms.alminshawi.telawat.player.PlaybackService { *; }
-keep class ialorabi.ms.alminshawi.telawat.data.Surah { *; }
-keep class ialorabi.ms.alminshawi.telawat.data.SurahRepository { *; }

-dontwarn com.google.common.util.concurrent.ListenableFuture