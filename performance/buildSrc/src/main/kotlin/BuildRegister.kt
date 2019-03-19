/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

import com.ullink.slack.simpleslackapi.impl.SlackSessionFactory

import org.jetbrains.report.json.*

import java.io.FileInputStream
import java.io.IOException
import java.io.File
import java.io.OutputStreamWriter
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import java.util.Properties

/**
 * Task to produce regressions report and send it to slack. Requires a report with current benchmarks result
 * and path to analyzer tool
 *
 * @property currentBenchmarksReportFile  path to file with becnhmarks result
 * @property analyzer path to analyzer tool
 * @property bundleSize size of build
 */
open class BuildRegister : DefaultTask() {
    @Input
    lateinit var currentBenchmarksReportFile: String
    @Input
    lateinit var analyzer: String

    var bundleSize: Int? = null

    val buildInfoToken: Int = 4
    val compileTimeSamplesNumber: Int = 2
    val buildNumberTokens: Int = 3
    val performanceServer = "https://kotlin-native-perf-summary.labs.jb.gg"

    private fun sendPostRequest(url: String, body: String) : String {
        val connection = URL(url).openConnection() as HttpURLConnection
        return connection.apply {
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            requestMethod = "POST"
            doOutput = true
            val outputWriter = OutputStreamWriter(outputStream)
            outputWriter.write(body)
            outputWriter.flush()
        }.let {
            if (it.responseCode == 200) it.inputStream else it.errorStream
        }.let { streamToRead ->
            BufferedReader(InputStreamReader(streamToRead)).use {
                val response = StringBuffer()

                var inputLine = it.readLine()
                while (inputLine != null) {
                    response.append(inputLine)
                    inputLine = it.readLine()
                }
                it.close()
                response.toString()
            }
        }
    }

    @TaskAction
    fun run() {
        // Get TeamCity properties.
        val teamcityConfig = System.getenv("TEAMCITY_BUILD_PROPERTIES_FILE") ?:
            error("Can't load teamcity config!")

        val buildProperties = Properties()
        buildProperties.load(FileInputStream(teamcityConfig))
        val buildId = buildProperties.getProperty("teamcity.build.id")
        val bintrayUser = buildProperties.getProperty("bintray.user")
        val bintrayApiKey = buildProperties.getProperty("bintray.apikey")
        val teamCityUser = buildProperties.getProperty("teamcity.auth.userId")
        val teamCityPassword = buildProperties.getProperty("teamcity.auth.password")
        val buildNumber = buildProperties.getProperty("build.number")

        // Get summary information.
        val output = arrayOf("$analyzer", "summary", "-exec-samples", "all", "-compile", "samples",
                "-compile-samples", "HelloWorld,Videoplayer", "-codesize-samples", "all", "$currentBenchmarksReportFile")
                .runCommand()
        // Postprocess information.
        val buildInfoParts = output.split(',')
        if (buildInfoParts.size != buildInfoToken) {
            error("Problems with getting summary information using $analyzer and $currentBenchmarksReportFile.")
        }

        val (failures, executionTime, compileTime, codeSize) = buildInfoParts.map { it.trim() }
        // Add legends.
        val geometricMean = "Geometric Mean-"
        val executionTimeInfo = "$geometricMean$executionTime"
        val codeSizeInfo = "$geometricMean$codeSize"
        val compileTimeSamples = compileTime.split(';')
        if (compileTimeSamples.size != compileTimeSamplesNumber) {
            error("Problems with getting compile time samples value. Expected at least $compileTimeSamplesNumber samples, got ${compileTimeSamples.size}")
        }
        val (helloWorldCompile, videoplayerCompile) = compileTimeSamples
        val compileTimeInfo = "HelloWorld-$helloWorldCompile;Videoplayer-$videoplayerCompile"
        val target = System.getProperty("os.name").replace("\\s".toRegex(), "")
        val buildNumberParts = buildNumber.split("-")
        if (buildNumberParts.size != buildNumberTokens) {
            error("Wrong format of build number $buildNumber.")
        }
        val (_, buildType, _) = buildNumberParts

        // Send post request to register build.
        val requestBody = buildString {
            append("{\"buildId\":\"$buildId\",")
            append("\"teamCityUser\":\"$teamCityUser\",")
            append("\"teamCityPassword\":\"$teamCityPassword\",")
            append("\"bintrayUser\": \"$bintrayUser\", ")
            append("\"bintrayPassword\":\"$bintrayApiKey\", ")
            append("\"target\": \"$target\",")
            append("\"buildType\": \"$buildType\",")
            append("\"failuresNumber\": $failures,")
            append("\"executionTime\": \"$executionTimeInfo\",")
            append("\"compileTime\": \"$compileTimeInfo\",")
            append("\"codeSize\": \"$codeSizeInfo\",")
            append("\"bundleSize\": ${bundleSize?.let {"\"$bundleSize\""} ?: bundleSize}}")
        }
        println(requestBody)
        println(sendPostRequest("$performanceServer/register", requestBody))
    }
}