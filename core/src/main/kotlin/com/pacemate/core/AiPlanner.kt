package com.pacemate.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

class AiException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * AI providers with an OpenAI-compatible chat API. Groq is the default
 * because it has a free tier and very cheap paid rates.
 */
enum class AiProvider(
    val label: String,
    val baseUrl: String,
    val defaultModel: String,
    val keyUrl: String,
    val modelHint: String,
) {
    GROQ(
        label = "Groq (free tier)",
        baseUrl = "https://api.groq.com/openai/v1",
        defaultModel = "llama-3.3-70b-versatile",
        keyUrl = "https://console.groq.com/keys",
        modelHint = "Cheapest: llama-3.1-8b-instant. Better plans: llama-3.3-70b-versatile.",
    ),
    KIMI(
        label = "Kimi (Moonshot)",
        baseUrl = "https://api.moonshot.ai/v1",
        defaultModel = "kimi-k2.6",
        keyUrl = "https://platform.moonshot.ai/console/api-keys",
        modelHint = "Use any model ID listed in your Moonshot console.",
    ),
}

/** Sends one HTTP POST; returns (status code, body). Replaceable in tests. */
fun interface HttpPost {
    fun post(url: String, headers: Map<String, String>, body: String): Pair<Int, String>
}

object UrlConnectionPost : HttpPost {
    override fun post(url: String, headers: Map<String, String>, body: String): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 20_000
            conn.readTimeout = 90_000
            conn.doOutput = true
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            return code to text
        } finally {
            conn.disconnect()
        }
    }
}

/**
 * AI features over an OpenAI-compatible chat-completions API (Groq or Kimi):
 *  - breaking a goal into realistic, human-sized steps
 *  - turning a free-text description of the user's day into a structured routine
 *
 * The scheduler itself is deterministic code; the model only does the parts
 * that need language understanding and a sense of how long things take people.
 */
class AiPlanner(
    private val provider: AiProvider,
    private val apiKey: String,
    model: String = "",
    private val http: HttpPost = UrlConnectionPost,
) : GoalBreakdowner {

    private val model = model.trim().ifBlank { provider.defaultModel }

    override suspend fun breakdown(
        request: GoalRequest,
        profile: UserProfile,
        pace: PaceStats,
        today: String,
    ): BreakdownResult {
        val prefs = profile.preferences
        val system = """
            You are the planning brain of PaceMate, a calm to-do app that plans at real human speed.
            Break the user's goal into concrete, sequential steps a real person can start without further thinking.

            Rules:
            - Each step is one clear action that starts with a verb ("Draft the introduction", not "Introduction").
            - estimateMinutes is how long an AVERAGE person really needs, including small interruptions,
              not an idealised best case. The app applies the user's personal pace on top, so do not adjust for it.
            - Keep steps between 15 and 180 minutes; split anything bigger.
            - difficulty: 1 = light/admin, 2 = normal, 3 = demanding deep work that needs peak energy.
            - Include planning at the start and a review step at the end when it is useful.
            - Be realistic about the total: do not inflate it, and do not compress it to fit the deadline.
            - category: one lowercase word, e.g. study, writing, coding, home, fitness, admin, creative, general.
            - note: one or two friendly sentences about the approach.

            Reply with only a JSON object of exactly this shape:
            {"subtasks":[{"title":"string","estimateMinutes":30,"difficulty":2,"notes":"string"}],
             "category":"string","note":"string"}
        """.trimIndent()

        val user = buildString {
            appendLine("Today is $today.")
            appendLine("Goal: ${request.title}")
            if (request.details.isNotBlank()) appendLine("Details: ${request.details}")
            appendLine("Deadline: ${request.deadline}")
            request.userEstimateHours?.let { appendLine("The user guesses it takes about $it hours in total.") }
            appendLine("Their usual focus session is ${prefs.focusBlockMinutes} minutes.")
            if (prefs.notes.isNotBlank()) appendLine("About the user: ${prefs.notes}")
            appendLine(PaceModel.describe(pace))
        }

        val text = chat(system, user)
        val parsed = try {
            PaceJson.decodeFromString<BreakdownResult>(extractJson(text))
        } catch (e: Exception) {
            throw AiException("Could not read the AI's plan. Please try again.", e)
        }
        val clean = parsed.subtasks
            .filter { it.title.isNotBlank() }
            .map { it.copy(estimateMinutes = it.estimateMinutes.coerceIn(5, 480), difficulty = it.difficulty.coerceIn(1, 3)) }
        if (clean.isEmpty()) throw AiException("The AI returned no steps. Please add more detail to the goal.")
        return parsed.copy(subtasks = clean, category = parsed.category.lowercase().ifBlank { "general" })
    }

    /** Extracts a structured routine from the user's own words. */
    suspend fun parseRoutine(description: String, current: UserProfile): RoutineDraft {
        val system = """
            You turn a person's description of their typical day into structured data for a planning app.
            Times are 24-hour "HH:MM". Days are ISO numbers: 1 = Monday ... 7 = Sunday.
            Use null for anything the person did not mention; never invent details.
            peakEnergy is one of "MORNING", "AFTERNOON", "EVENING" (or null).
            maxTaskHoursWorkday / maxTaskHoursFreeDay: hours per day they are happy to spend on personal tasks
            outside their job, only if they said or clearly implied it.
            summary: one short friendly sentence confirming what you understood.

            Reply with only a JSON object with these keys (any value may be null except summary):
            {"wakeTime":"07:00","sleepTime":"23:00","workStart":"09:00","workEnd":"17:00",
             "workDays":[1,2,3,4,5],"commuteMinutes":30,
             "meals":[{"label":"Lunch","start":"13:00","durationMinutes":45,"days":[]}],
             "fixedBlocks":[{"label":"Gym","start":"19:00","durationMinutes":60,"days":[2,4]}],
             "focusBlockMinutes":50,"breakMinutes":10,"peakEnergy":"MORNING",
             "maxTaskHoursWorkday":2,"maxTaskHoursFreeDay":4,"restDays":[7],"summary":"string"}
        """.trimIndent()
        val user = "Current settings (for context only): ${PaceJson.encodeToString(current.routine)}\n\n" +
            "How the person describes their day:\n$description"
        val text = chat(system, user)
        return try {
            PaceJson.decodeFromString<RoutineDraft>(extractJson(text))
        } catch (e: Exception) {
            throw AiException("Could not understand the routine description. Please fill it in manually.", e)
        }
    }

    /** One chat-completions call in JSON mode; returns the assistant's text. */
    internal suspend fun chat(system: String, user: String): String = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("model", model)
            put("temperature", 0.3)
            put("max_tokens", 4096)
            put("response_format", buildJsonObject { put("type", "json_object") })
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", system) })
                add(buildJsonObject { put("role", "user"); put("content", user) })
            })
        }
        val headers = mapOf(
            "Authorization" to "Bearer $apiKey",
            "Content-Type" to "application/json",
            "Accept" to "application/json",
        )
        val (code, text) = try {
            http.post("${provider.baseUrl}/chat/completions", headers, body.toString())
        } catch (e: SocketTimeoutException) {
            throw AiException("The AI took too long to answer. Please try again.", e)
        } catch (e: IOException) {
            throw AiException("No connection to the AI. Check your internet.", e)
        }
        if (code !in 200..299) throw AiException(errorMessage(code, text))
        parseContent(text)
    }

    private fun errorMessage(code: Int, body: String): String {
        val detail = runCatching {
            PaceJson.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
        }.getOrNull()
        return when (code) {
            401, 403 -> "Your ${provider.label} API key was rejected. Check it in Settings."
            404 -> "Model \"$model\" wasn't found at ${provider.label}. Check the model name in Settings."
            429 -> "Rate limit reached at ${provider.label}. Wait a minute and try again."
            in 500..599 -> "${provider.label} is having trouble right now. Please try again shortly."
            else -> "AI request failed ($code)" + (detail?.let { ": $it" } ?: ".")
        }
    }

    companion object {
        /** Reads `choices[0].message.content` from a chat-completions response. */
        fun parseContent(responseBody: String): String {
            val root = try {
                PaceJson.parseToJsonElement(responseBody).jsonObject
            } catch (e: Exception) {
                throw AiException("The AI sent an unreadable answer. Please try again.", e)
            }
            val choice = (root["choices"] as? JsonArray)?.firstOrNull()?.jsonObject
                ?: throw AiException("The AI returned no answer. Please try again.")
            val finish = (choice["finish_reason"] as? JsonPrimitive)?.content
            if (finish == "length") throw AiException("The AI's answer was cut off. Try a smaller goal.")
            val content = (choice["message"] as? JsonObject)?.get("content")?.jsonPrimitive?.content
            if (content.isNullOrBlank()) throw AiException("The AI returned an empty answer. Please try again.")
            return content
        }

        /** Strips code fences or chatter around the JSON object, if a model adds any. */
        fun extractJson(text: String): String {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            return if (start >= 0 && end > start) text.substring(start, end + 1) else text
        }
    }
}
