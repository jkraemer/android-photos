# Shrink and optimise only; readable names keep stack traces usable without a mapping file.
-dontobfuscate

# ML Kit reports every text recognition run to Play services
# (TelemetryLogging, API "mlkit:vision"). zzue.zzc builds and sends that
# report; it returns nothing, so R8 drops the calls. The class name is
# obfuscated by Google and may change with an ML Kit upgrade.
-assumenosideeffects class com.google.android.gms.internal.mlkit_vision_text_common.zzue {
    void zzc(int, int, long, long);
}

# Play services' own telemetry for API calls made through it; its callers
# ignore the result.
-assumenosideeffects interface com.google.android.gms.common.internal.TelemetryLoggingClient {
    com.google.android.gms.tasks.Task log(com.google.android.gms.common.internal.TelemetryData);
}

# Fails the build if any telemetry send survives, e.g. when an ML Kit
# upgrade renames the class above and its rule stops matching.
-checkdiscard class * implements com.google.android.gms.common.internal.TelemetryLoggingClient {
    com.google.android.gms.tasks.Task log(com.google.android.gms.common.internal.TelemetryData);
}
