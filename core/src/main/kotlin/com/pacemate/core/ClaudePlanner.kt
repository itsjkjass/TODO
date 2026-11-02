package com.pacemate.core

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

class AiException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * AI features backed by Claude through the official Anthropic SDK:
 *  - breaking a goal into realistic, human-sized steps
 *  - turning a free-text description of the user's day into a structured routine
 *
 * The scheduler itself is deterministic code; the model only does the parts
 * that need understanding of language and of how long things take people.
 */
class ClaudePlanner(
    apiKey: String,
    private val model: String = DEFAULT_MODEL,
) : GoalBreakdowner {

    companion object {
        const val DEFAULT_MODEL = "claude-opus-5-5"
    }

    private val client: AnthropicClient = AnthropicOkHttpClient.builder()
        .apiKey(apiKey)
        .build()

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
              The scheduler decides whether it fits.
            - category: one lowercase word, e.g. study, writing, coding, home, fitness, admin, creative, general.
            - note: one or two friendly sentences about the approach.
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

        val text = call(system, user, breakdownSchema(), OutputConfig.Effort.MEDIUM)
        val parsed = try {
            PaceJson.decodeFromString<BreakdownResult>(text)
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
            peakEnergy is one of MORNING, AFTERNOON, EVENING (or null).
            maxTaskHoursWorkday / maxTaskHoursFreeDay: hours per day they are happy to spend on personal tasks
            outside their job, only if they said or clearly implied it.
            summary: one short friendly sentence confirming what you understood.
        """.trimIndent()
        val user = "Current settings (for context only): ${PaceJson.encodeToString(current.routine)}\n\n" +
            "How the person describes their day:\n$description"
        val text = call(system, user, routineSchema(), OutputConfig.Effort.LOW)
        return try {
            PaceJson.decodeFromString<RoutineDraft>(text)
        } catch (e: Exception) {
            throw AiException("Could not understand the routine description. Please fill it in manually.", e)
        }
    }

    private suspend fun call(
        system: String,
        user: String,
        schema: Map<String, Any?>,
        effort: OutputConfig.Effort,
    ): String = withContext(Dispatchers.IO) {
        val params = MessageCreateParams.builder()
            .model(model)
            .maxTokens(16000L)
            .system(system)
            .addUserMessage(user)
            .outputConfig(
                OutputConfig.builder()
                    .effort(effort)
                    .format(
                        JsonOutputFormat.builder()
                            .schema(
                                JsonOutputFormat.Schema.builder()
                                    .putAllAdditionalProperties(schema.mapValues { JsonValue.from(it.value) })
                                    .build(),
                            )
                            .build(),
                    )
                    .build(),
            )
            // Server-side refusal fallback: if a safety classifier declines, the
            // request is retried on a suitable model instead of failing outright.
            .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
            .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
            .build()

        val message = try {
            client.messages().create(params)
        } catch (e: Exception) {
            throw AiException(friendlyError(e), e)
        }

        val stop = message.stopReason().map { it.toString() }.orElse("")
        if (stop == "refusal") throw AiException("The AI declined this request. Try rephrasing the goal.")
        if (stop == "max_tokens") throw AiException("The AI's answer was cut off. Try a smaller goal.")

        val text = message.content().mapNotNull { block -> block.text().map { it.text() }.orElse(null) }
            .joinToString("")
        if (text.isBlank()) throw AiException("The AI returned an empty answer. Please try again.")
        text
    }

    private fun friendlyError(e: Exception): String {
        val name = e::class.simpleName ?: ""
        return when {
            "Unauthorized" in name || "Authentication" in name -> "Your Anthropic API key was rejected. Check it in Settings."
            "RateLimit" in name -> "Too many requests right now. Please wait a minute and retry."
            "Io" in name || "Connect" in name || e is java.io.IOException -> "No connection to the AI. Check your internet."
            else -> "AI request failed: ${e.message ?: name}"
        }
    }

    // ---- JSON schemas for structured outputs ----

    private fun obj(props: Map<String, Any?>): Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to props,
        "required" to props.keys.toList(),
        "additionalProperties" to false,
    )

    private fun nullable(type: String): Map<String, Any?> = mapOf("anyOf" to listOf(mapOf("type" to type), mapOf("type" to "null")))

    private fun breakdownSchema(): Map<String, Any?> = obj(
        mapOf(
            "subtasks" to mapOf(
                "type" to "array",
                "items" to obj(
                    mapOf(
                        "title" to mapOf("type" to "string"),
                        "estimateMinutes" to mapOf("type" to "integer"),
                        "difficulty" to mapOf("type" to "integer", "enum" to listOf(1, 2, 3)),
                        "notes" to mapOf("type" to "string"),
                    ),
                ),
            ),
            "category" to mapOf("type" to "string"),
            "note" to mapOf("type" to "string"),
        ),
    )

    private fun routineSchema(): Map<String, Any?> {
        val block = obj(
            mapOf(
                "label" to mapOf("type" to "string"),
                "start" to mapOf("type" to "string"),
                "durationMinutes" to mapOf("type" to "integer"),
                "days" to mapOf("type" to "array", "items" to mapOf("type" to "integer")),
            ),
        )
        val nullableBlocks = mapOf("anyOf" to listOf(mapOf("type" to "array", "items" to block), mapOf("type" to "null")))
        val nullableDays = mapOf("anyOf" to listOf(mapOf("type" to "array", "items" to mapOf("type" to "integer")), mapOf("type" to "null")))
        return obj(
            mapOf(
                "wakeTime" to nullable("string"),
                "sleepTime" to nullable("string"),
                "workStart" to nullable("string"),
                "workEnd" to nullable("string"),
                "workDays" to nullableDays,
                "commuteMinutes" to nullable("integer"),
                "meals" to nullableBlocks,
                "fixedBlocks" to nullableBlocks,
                "focusBlockMinutes" to nullable("integer"),
                "breakMinutes" to nullable("integer"),
                "peakEnergy" to nullable("string"),
                "maxTaskHoursWorkday" to nullable("number"),
                "maxTaskHoursFreeDay" to nullable("number"),
                "restDays" to nullableDays,
                "summary" to mapOf("type" to "string"),
            ),
        )
    }
}
