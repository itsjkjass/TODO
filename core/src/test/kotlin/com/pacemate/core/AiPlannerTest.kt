package com.pacemate.core

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AiPlannerTest {
    private class FakeHttp(val code: Int, val content: String, val raw: Boolean = false) : HttpPost {
        var url = ""
        var headers = emptyMap<String, String>()
        var body = ""
        override fun post(url: String, headers: Map<String, String>, body: String): Pair<Int, String> {
            this.url = url; this.headers = headers; this.body = body
            if (raw) return code to content
            val escaped = kotlinx.serialization.json.JsonPrimitive(content).toString()
            return code to """{"choices":[{"index":0,"finish_reason":"stop","message":{"role":"assistant","content":$escaped}}]}"""
        }
    }

    private val request = GoalRequest("Write essay", "2000 words", "2026-12-01")

    @Test
    fun groqRequestIsOpenAiCompatibleJsonMode() = runBlocking {
        val http = FakeHttp(200, """{"subtasks":[{"title":"Outline","estimateMinutes":30,"difficulty":1,"notes":""}],"category":"Writing","note":"ok"}""")
        val r = AiPlanner(AiProvider.GROQ, "gsk_test", "", http).breakdown(request, UserProfile(), PaceStats(), "2026-11-02")

        assertEquals("https://api.groq.com/openai/v1/chat/completions", http.url)
        assertEquals("Bearer gsk_test", http.headers["Authorization"])
        val body = PaceJson.parseToJsonElement(http.body).jsonObject
        assertEquals(AiProvider.GROQ.defaultModel, body["model"]!!.jsonPrimitive.content)
        assertEquals("json_object", (body["response_format"] as JsonObject)["type"]!!.jsonPrimitive.content)
        assertEquals(2, body["messages"]!!.jsonArray.size)

        assertEquals("Outline", r.subtasks.single().title)
        assertEquals("writing", r.category)
    }

    @Test
    fun kimiUsesMoonshotEndpointAndCustomModel() = runBlocking {
        val http = FakeHttp(200, """{"subtasks":[{"title":"A","estimateMinutes":20,"difficulty":2,"notes":""}],"category":"general","note":""}""")
        AiPlanner(AiProvider.KIMI, "sk-x", "my-kimi-model", http).breakdown(request, UserProfile(), PaceStats(), "2026-11-02")
        assertEquals("https://api.moonshot.ai/v1/chat/completions", http.url)
        assertEquals("my-kimi-model", PaceJson.parseToJsonElement(http.body).jsonObject["model"]!!.jsonPrimitive.content)
    }

    @Test
    fun toleratesCodeFencesAndClampsValues() = runBlocking {
        val http = FakeHttp(200, "Here you go:\n```json\n{\"subtasks\":[{\"title\":\"Huge\",\"estimateMinutes\":9999,\"difficulty\":7,\"notes\":\"\"}],\"category\":\"\",\"note\":\"\"}\n```")
        val r = AiPlanner(AiProvider.GROQ, "k", "", http).breakdown(request, UserProfile(), PaceStats(), "2026-11-02")
        assertEquals(480, r.subtasks.single().estimateMinutes)
        assertEquals(3, r.subtasks.single().difficulty)
        assertEquals("general", r.category)
    }

    @Test
    fun parsesRoutineWithNulls() = runBlocking {
        val http = FakeHttp(200, """{"wakeTime":"06:30","sleepTime":null,"workDays":[1,2,3],"meals":null,"peakEnergy":"EVENING","summary":"Got it"}""")
        val d = AiPlanner(AiProvider.GROQ, "k", "", http).parseRoutine("I wake at 6:30", UserProfile())
        val merged = UserProfile().merge(d)
        assertEquals(6 * 60 + 30, merged.routine.wakeTime)
        assertEquals(EnergyPeak.EVENING, merged.preferences.peakEnergy)
        assertEquals(UserProfile().routine.meals, merged.routine.meals)
    }

    @Test
    fun friendlyErrors() {
        val cases = mapOf(401 to "rejected", 404 to "wasn't found", 429 to "Rate limit")
        for ((code, phrase) in cases) {
            val e = assertFailsWith<AiException> {
                runBlocking {
                    AiPlanner(AiProvider.GROQ, "k", "", FakeHttp(code, """{"error":{"message":"x"}}""", raw = true))
                        .breakdown(request, UserProfile(), PaceStats(), "2026-11-02")
                }
            }
            assertTrue(phrase in e.message!!, "$code -> ${e.message}")
        }
    }

    @Test
    fun cutOffAnswerIsReported() {
        val body = """{"choices":[{"finish_reason":"length","message":{"content":"{\"subt"}}]}"""
        assertFailsWith<AiException> { AiPlanner.parseContent(body) }
    }
}
