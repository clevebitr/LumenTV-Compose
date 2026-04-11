-printmapping build/release-mapping.txt
-libraryjars  <java.home>/jmods/java.base.jmod(!**.jar;!module-info.class)

# 核心保持规则
-keep class com.corner.** { *; }
-keep class MainKt { 
    public static void main(java.lang.String[]);
    *;
}

# 第三方库核心保持
-keep class io.ktor.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class kotlinx.serialization.** { *; }
-keep class org.koin.** { *; }
-keep class androidx.compose.** { *; }
-keep class com.seiko.imageloader.** { *; }

# 关键优化：禁止所有警告和笔记输出
-dontwarn **
-dontnote **
-dontusemixedcaseclassnames

# 特别针对 ANTLR 的警告
-dontwarn org.antlr.v4.runtime.**
-dontnote org.antlr.v4.runtime.**

# 保留必要属性
-keepattributes Signature,InnerClasses,*Annotation*

# 数据库相关
-keep class androidx.room.** { *; }

# 网络和工具
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keep class com.google.gson.** { *; }
-keep class org.json.** { *; }
-keep class cn.hutool.** { *; }

# 多媒体相关
-keep class uk.co.caprica.vlcj.** { *; }
-keep class uk.co.caprica.vlcj.player.** { *; }
-keep class uk.co.caprica.vlcj.factory.** { *; }
-keep class uk.co.caprica.vlcj.log.** { *; }
-keep interface uk.co.caprica.vlcj.** { *; }
-keep class * implements uk.co.caprica.vlcj.** { *; }
-keep,allowobfuscation class uk.co.caprica.vlcj.** {
    public static void main(java.lang.String[]);
    public static ** valueOf(java.lang.String);
    public static ** valueOf(int);
    public static ** of(...);
    public static ** create(...);
}
-keep class org.jupnp.** { *; }

# 日志框架（项目使用 Log4j2）
-keep class org.apache.logging.log4j.** { *; }
-keep class org.slf4j.** { *; }


-keep class org.jboss.marshalling.** {*;}
-keep class org.sqlite.** {*;}
-keep class org.eclipse.jetty.** {*;}
-keep interface org.eclipse.jetty.** {*;}
-keep class com.sun.jna.** {*;}
-keep class javax.servlet.** { *; }
-keep class java.lang.invoke.** {*;}
# TLS/SSL 相关
-keep class org.osgi.** {*;}
-keep class com.google.appengine.** {*;}
-keep class com.google.apphosting.** {*;}
-keep class com.google.zxing.** {*;}
-keep class org.apache.** {*;}
-keep class org.openjsse.** {*;}
-keep class com.jcraft.** {*;}

# 压缩库
-keep class com.aayushatharva.** {*;}
-keep class com.github.luben.** {*;}
-keep class com.google.protobuf.** {*;}

# Jackson
-keep class org.codehaus.** {*;}

# 其他依赖
-keep class com.github.sardine.** {*;}
-keep class com.google.common.** {*;}
-keep class org.jsoup.** {*;}
-keep class kotlinx.html.** {*;}
-keep class kotlinx.io.** {*;}
-keep class javax.ws.rs.** { *; }
-keep class javax.enterprise.** { *; }
-keep class javax.inject.** { *; }
-keep class javax.annotation.** { *; }
-keep class org.fourthline.cling.** { *; }
# Ktor 网络相关的类和方法
-keep class io.ktor.network.sockets.SocketBase { *; }
-keep class io.ktor.utils.io.ChannelJob { *; }
-keepclassmembers class io.ktor.network.sockets.SocketBase {
    public <methods>;
}

# Kotlin 内部的部分
-keep class kotlinx.atomicfu.AtomicRef { *; }
-keep class kotlin.jvm.functions.Function0 { *; }

# kotlinx.serialization 优化修复
# see https://github.com/Kotlin/kotlinx.serialization/issues/2719
-keepclassmembers public class **$$serializer {
    private ** descriptor;
}

# CatVod 爬虫相关
-keep class com.github.catvod.** { *; }
-keep class com.corner.init.** { *; }
-keep class com.corner.server.** { *; }

# 针对性禁用警告（避免掩盖真实问题）
-dontwarn io.ktor.**
-dontwarn org.jboss.marshalling.**
-dontwarn reactor.blockhound.**
-dontwarn com.oracle.svm.**
-dontwarn com.sun.activation.**
-dontwarn org.graalvm.nativeimage.**
-dontwarn com.google.appengine.**
-dontwarn com.google.apphosting.**
-dontwarn org.apache.**
-dontwarn okhttp3.internal.platform.android.**
-dontwarn okhttp3.internal.platform.Android10Platform.**
-dontwarn okhttp3.internal.platform.AndroidPlatform.**
-dontwarn android.**
-dontwarn org.openjsse.**
-dontwarn io.netty.**
-dontwarn org.eclipse.jetty.**
-dontwarn java.lang.invoke.**
-dontwarn cn.hutool.**
-dontwarn javax.microedition.**
-dontwarn javax.persistence.**
-dontwarn org.xmlpull.**
-dontwarn org.dbunit.**
-dontwarn javax.enterprise.**
-dontwarn org.hibernate.**
-dontwarn javax.inject.**
-dontwarn javax.ws.**
-dontwarn javax.mail.**
-dontwarn org.apache.tools.ant.**

# 禁用不必要的笔记输出
-dontnote org.antlr.v4.runtime.**
-dontnote okhttp3.internal.platform.android.**

# 其他优化选项
-dontusemixedcaseclassnames
-verbose

