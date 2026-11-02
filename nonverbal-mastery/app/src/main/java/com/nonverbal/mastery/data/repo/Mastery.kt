package com.nonverbal.mastery.data.repo

import com.nonverbal.mastery.data.db.LevelProgress
import com.nonverbal.mastery.data.db.LevelStatus
import com.nonverbal.mastery.data.models.Level
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One pass through a level's questions. The last attempt in the list is the current one. */
@Serializable
data class QuizAttempt(
    val startedAt: Long,
    val answers: List<Int?>,
    val completedAt: Long? = null,
    val score: Int? = null,
)

/** The structured field note behind "field mission logged". */
@Serializable
data class FieldNote(
    val assignment: Int = 0,
    val observed: String = "",
    val hypothesis: String = "",
    val disconfirmer: String = "",
    val outcome: String = "",
    val loggedAt: Long? = null,
) {
    val isComplete: Boolean
        get() = observed.trim().length >= 20 && hypothesis.trim().length >= 10 && disconfirmer.trim().length >= 10
}

object Codec {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun attempts(p: LevelProgress?): List<QuizAttempt> =
        runCatching { json.decodeFromString<List<QuizAttempt>>(p?.quizAnswersJson ?: "[]") }.getOrDefault(emptyList())

    fun encodeAttempts(list: List<QuizAttempt>): String = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(QuizAttempt.serializer()), list)

    fun fieldNote(p: LevelProgress?): FieldNote =
        p?.fieldNote?.takeIf { it.isNotBlank() }?.let { runCatching { json.decodeFromString<FieldNote>(it) }.getOrNull() } ?: FieldNote()

    fun encodeFieldNote(n: FieldNote): String = json.encodeToString(FieldNote.serializer(), n)
}

const val MASTERY_QUIZ_THRESHOLD = 80

/** What a level still needs before it can be mastered, in plain words. */
data class MasteryCheck(
    val drillsDone: Int,
    val drillsTotal: Int,
    val quizAnswered: Int,
    val quizTotal: Int,
    val quizScore: Int?,
    val fieldLogged: Boolean,
    val reflectionLength: Int,
    val reflectionMin: Int,
) {
    val drillsMet get() = drillsDone >= drillsTotal
    val quizMet get() = quizScore != null && quizScore >= MASTERY_QUIZ_THRESHOLD
    val reflectionMet get() = reflectionLength >= reflectionMin
    val ready get() = drillsMet && quizMet && fieldLogged && reflectionMet

    /** Never "not ready" — always the specific list. */
    fun remaining(): List<String> = buildList {
        if (!drillsMet) {
            val left = drillsTotal - drillsDone
            add("complete $left ${if (left == 1) "drill" else "drills"}")
        }
        when {
            quizScore == null && quizAnswered < quizTotal ->
                add("finish the quiz (${quizAnswered} of $quizTotal answered) and score at least $MASTERY_QUIZ_THRESHOLD%")
            quizScore != null && quizScore < MASTERY_QUIZ_THRESHOLD ->
                add("retake the quiz: $quizScore% this time, $MASTERY_QUIZ_THRESHOLD% is the bar")
        }
        if (!fieldLogged) add("log a field mission")
        if (!reflectionMet) {
            val more = reflectionMin - reflectionLength
            add("write $more more ${if (more == 1) "character" else "characters"} of reflection")
        }
    }

    companion object {
        fun of(level: Level, p: LevelProgress?): MasteryCheck {
            val attempts = Codec.attempts(p)
            val current = attempts.lastOrNull()
            val lastCompleted = attempts.lastOrNull { it.completedAt != null }
            val drillCount = level.drills.size
            val mask = p?.drillsDone ?: 0
            return MasteryCheck(
                drillsDone = (0 until drillCount).count { mask and (1 shl it) != 0 },
                drillsTotal = drillCount,
                quizAnswered = current?.answers?.count { it != null } ?: 0,
                quizTotal = level.quiz.size,
                quizScore = lastCompleted?.score,
                fieldLogged = p?.fieldLogged == true,
                reflectionLength = p?.reflection?.trim()?.length ?: 0,
                reflectionMin = level.reflection.minCharacters,
            )
        }
    }
}

fun scoreOf(level: Level, answers: List<Int?>): Int {
    if (level.quiz.isEmpty()) return 0
    val right = level.quiz.indices.count { answers.getOrNull(it) == level.quiz[it].answer }
    return Math.round(right * 100f / level.quiz.size)
}

/** Sequential unlocking within a category; the first written level of every category is open. */
object Unlocks {
    fun unlockedIds(levelsInOrder: List<Level>, progress: Map<String, LevelProgress>): Set<String> {
        val open = mutableSetOf<String>()
        var previousMastered = true
        for (level in levelsInOrder) {
            val p = progress[level.id]
            val started = p != null && (p.status == LevelStatus.IN_PROGRESS || p.status == LevelStatus.MASTERED)
            if (previousMastered || started) open += level.id
            previousMastered = p?.status == LevelStatus.MASTERED
        }
        return open
    }
}
