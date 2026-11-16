# FolioScan R8 ProGuard Rules
-keep class com.folioscan.core.model.** { *; }
-keep class org.opencv.** { *; }
-keep class net.zetetic.** { *; }
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn org.bouncycastle.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
