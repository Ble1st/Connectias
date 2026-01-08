package com.ble1st.connectias.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Startup Benchmark für Connectias App.
 * 
 * Phase 8: Performance & Monitoring
 * 
 * Misst die App-Startzeit mit verschiedenen Compilation-Modi:
 * - None: Keine Optimierung (Baseline)
 * - Baseline Profile: Mit generiertem Baseline Profile
 * - Full: Vollständig AOT-kompiliert
 * 
 * Führe aus mit:
 * ./gradlew :benchmark:connectedBenchmarkAndroidTest
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupNoCompilation() {
        benchmark(CompilationMode.None())
    }

    @Test
    fun startupBaselineProfile() {
        benchmark(CompilationMode.Partial(BaselineProfileMode.Require))
    }

    @Test
    fun startupFullCompilation() {
        benchmark(CompilationMode.Full())
    }

    private fun benchmark(compilationMode: CompilationMode) {
        rule.measureRepeated(
            packageName = "com.ble1st.connectias",
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilationMode,
            startupMode = StartupMode.COLD,
            iterations = 10
        ) {
            pressHome()
            startActivityAndWait()
            
            // Warte auf vollständiges Laden
            device.waitForIdle()
        }
    }
}
