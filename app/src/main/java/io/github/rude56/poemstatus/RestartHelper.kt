package io.github.rude56.poemstatus

import android.content.Context
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext

object RestartHelper {
    @Suppress("UNUSED_PARAMETER")
    suspend fun restartSystemUi(context: Context): RestartResult = withContext(Dispatchers.IO) {

        val authorization = runSu("id -u", rootProbe = true)
        if (authorization != RestartResult.SUCCESS) {
            return@withContext authorization
        }
        for (command in arrayOf("killall com.android.systemui", "pkill -f com.android.systemui")) {
            val result = runSu(command, rootProbe = false)
            if (result == RestartResult.SUCCESS) {
                Log.i("SystemUI restart requested")
                return@withContext result
            }
            if (result != RestartResult.COMMAND_FAILED) {
                return@withContext result
            }

        }
        RestartResult.COMMAND_FAILED
    }

    private suspend fun runSu(command: String, rootProbe: Boolean): RestartResult = runInterruptible {
        val process = try {
            ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        } catch (e: IOException) {
            val result = RestartFailureClassifier.classifySuStartFailure(e)
            Log.w("SystemUI restart could not start su: $result")
            return@runInterruptible result
        } catch (e: SecurityException) {
            Log.w("SystemUI restart process launch blocked")
            return@runInterruptible RestartResult.COMMAND_FAILED
        }

        try {

            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            val result = if (rootProbe) {
                RestartFailureClassifier.classifyRootProbe(exitCode, output)
            } else {
                RestartFailureClassifier.classifyCommand(exitCode, output)
            }
            if (result != RestartResult.SUCCESS) {
                Log.w("SystemUI restart ${if (rootProbe) "root probe" else "command"}: $result (exit=$exitCode)")
            }
            result
        } catch (e: IOException) {

            Log.w("SystemUI restart output read failed")
            RestartResult.COMMAND_FAILED
        } finally {
            process.destroy()
        }
    }
}
