

# Pleos SDK: TextToSpeech·SpeechToText·NaviHelper AAR에는 자체 keep 규칙이 없다.
# AIDL 인터페이스·Parcelable 데이터로 서비스에 바인딩하므로 이름과 멤버를 바꾸지 않게 통째로 지킨다.
-keep class ai.pleos.playground.** { *; }
-keep interface ai.pleos.playground.** { *; }
-dontwarn ai.pleos.playground.**

# AAOS android.car는 플랫폼이 제공한다(useLibrary). 컴파일 시에만 있다.
-dontwarn android.car.**

# Pleos Vehicle SDK는 Gson TypeToken으로 응답을 파싱한다. R8 full mode는 제네릭 시그니처를 지워
# "TypeToken must be created with a type argument" 오류가 난다(release 에뮬레이터에서 확인). Gson 공식 R8 규칙.
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken
