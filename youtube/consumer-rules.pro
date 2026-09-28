# NewPipeExtractor runs YouTube's own player JavaScript through Rhino to solve the signature and
# the `n` parameter, and Rhino reaches for its classes reflectively - a minified host app that
# shrank them would fail at extraction time, not at build time, and only on release builds.
-keep class org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.**

# The extractor itself reads YouTube's answers by name, and its service list is loaded reflectively.
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**

# jsoup and nanojson come with it; both are used through reflection-free APIs but ship classes the
# extractor names in strings.
-dontwarn org.jsoup.**
-dontwarn com.grack.nanojson.**
