package com.nonverbal.mastery.engine

import com.nonverbal.mastery.content.TestContent
import com.nonverbal.mastery.data.db.LevelProgress
import com.nonverbal.mastery.data.db.LevelStatus
import com.nonverbal.mastery.data.db.PoseBookmark
import com.nonverbal.mastery.data.db.ReviewLog
import com.nonverbal.mastery.data.repo.Codec
import com.nonverbal.mastery.data.repo.FieldNote
import com.nonverbal.mastery.data.repo.JournalBackup
import com.nonverbal.mastery.data.repo.MasteryCheck
import com.nonverbal.mastery.data.repo.ProgressRepository
import com.nonverbal.mastery.data.repo.QuizAttempt
import com.nonverbal.mastery.data.repo.ReviewScheduler
import com.nonverbal.mastery.data.repo.Unlocks
import com.nonverbal.mastery.data.repo.scoreOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class ReviewSchedulerTest {
    private val day = TimeUnit.DAYS.toMillis(1)

    @Test
    fun followsTheSpecifiedIntervalsThenRetains() {
        var p = LevelProgress("x", "c", LevelStatus.MASTERED, masteredAt = 0L)
        var now = 0L
        p = ReviewScheduler.onMastered(p, now)
        assertEquals(1 * day, p.nextReviewAt)
        val expected = listOf(3, 7, 21, 60, 120)
        for (gap in expected) {
            now = p.nextReviewAt!!
            assertTrue(ReviewScheduler.isDue(p, now))
            p = ReviewScheduler.onReviewed(p, now)
            assertEquals(now + gap * day, p.nextReviewAt)
        }
        now = p.nextReviewAt!!
        p = ReviewScheduler.onReviewed(p, now)
        assertNull("after six returns the chapter is retained", p.nextReviewAt)
        assertTrue(ReviewScheduler.isRetained(p))
        assertEquals(6, p.reviewStreak)
    }

    @Test
    fun missingADueDateLocksNothingAndKeepsItDue() {
        val p = ReviewScheduler.onMastered(LevelProgress("x", "c", LevelStatus.MASTERED, masteredAt = 0L), 0L)
        val muchLater = 40 * day
        assertTrue(ReviewScheduler.isDue(p, muchLater))
        assertEquals(39, ReviewScheduler.daysWaiting(p, muchLater))
        assertEquals(LevelStatus.MASTERED, p.status)
    }
}

class MasteryTest {
    private val curriculum = TestContent.curriculum
    private val level = curriculum.levels.getValue("foundations-01")

    private fun allDrills() = (1 shl level.drills.size) - 1
    private fun perfect() = level.quiz.map { it.answer }

    @Test
    fun scoreRoundsToPercent() {
        assertEquals(100, scoreOf(level, perfect()))
        val oneWrong = perfect().toMutableList<Int?>().also { it[0] = (it[0]!! + 1) % 4 }
        assertEquals(Math.round((level.quiz.size - 1) * 100f / level.quiz.size), scoreOf(level, oneWrong))
    }

    @Test
    fun remainingListIsSpecific() {
        val fresh = MasteryCheck.of(level, null)
        assertFalse(fresh.ready)
        val lines = fresh.remaining()
        assertTrue(lines.any { it.startsWith("complete ${level.drills.size} drills") })
        assertTrue(lines.any { it.startsWith("finish the quiz") })
        assertTrue(lines.contains("log a field mission"))
        assertTrue(lines.any { it.startsWith("write 150 more characters") })
    }

    @Test
    fun readyOnlyWhenEveryCriterionIsMet() {
        val attempt = QuizAttempt(0L, perfect(), completedAt = 1L, score = 100)
        val done = LevelProgress(
            level.id, level.categoryId, LevelStatus.IN_PROGRESS,
            quizAnswersJson = Codec.encodeAttempts(listOf(attempt)),
            drillsDone = allDrills(), fieldLogged = true, reflection = "x".repeat(150),
        )
        assertTrue(MasteryCheck.of(level, done).ready)
        assertFalse(MasteryCheck.of(level, done.copy(reflection = "x".repeat(149))).ready)
        assertFalse(MasteryCheck.of(level, done.copy(fieldLogged = false)).ready)
        assertFalse(MasteryCheck.of(level, done.copy(drillsDone = allDrills() xor 1)).ready)
        val weak = QuizAttempt(0L, perfect(), completedAt = 1L, score = 67)
        val weakProgress = done.copy(quizAnswersJson = Codec.encodeAttempts(listOf(weak)))
        assertFalse(MasteryCheck.of(level, weakProgress).ready)
        assertTrue(MasteryCheck.of(level, weakProgress).remaining().single().startsWith("retake the quiz: 67%"))
    }

    @Test
    fun aNewAttemptKeepsTheLastCompletedScoreUntilItFinishes() {
        val good = QuizAttempt(0L, perfect(), completedAt = 1L, score = 100)
        val retake = QuizAttempt(2L, List(level.quiz.size) { null })
        val p = LevelProgress(level.id, level.categoryId, LevelStatus.IN_PROGRESS, quizAnswersJson = Codec.encodeAttempts(listOf(good, retake)))
        val check = MasteryCheck.of(level, p)
        assertEquals(100, check.quizScore)
        assertEquals(0, check.quizAnswered)
    }

    @Test
    fun unlocksAreSequentialOverWrittenLevelsAndEveryCategoryStartsOpen() {
        val foundations = curriculum.levelsInOrder("foundations")
        assertEquals(foundations.map { it.index }.sorted(), foundations.map { it.index })
        // Unlocking skips over chapters that are not written yet: simulate a part with gaps.
        val gappy = foundations.filter { it.index in setOf(1, 11, 15) }
        assertEquals(listOf(1, 11, 15), gappy.map { it.index })
        val none = Unlocks.unlockedIds(gappy, emptyMap())
        assertEquals(setOf("foundations-01"), none)
        val m1 = mapOf("foundations-01" to LevelProgress("foundations-01", "foundations", LevelStatus.MASTERED))
        assertEquals(setOf("foundations-01", "foundations-11"), Unlocks.unlockedIds(gappy, m1))
        // Over the real, complete part, mastering the first opens exactly the second.
        assertEquals(setOf(foundations[0].id, foundations[1].id), Unlocks.unlockedIds(foundations, m1))
        // A level the reader has already started stays open even if content is later inserted before it.
        val started = mapOf("foundations-15" to LevelProgress("foundations-15", "foundations", LevelStatus.IN_PROGRESS))
        assertTrue("foundations-15" in Unlocks.unlockedIds(foundations, started))
    }

    @Test
    fun fieldNoteNeedsTheDisconfirmer() {
        val partial = FieldNote(observed = "Hand to neck two seconds after the question", hypothesis = "the date worries him")
        assertFalse(partial.isComplete)
        assertTrue(partial.copy(disconfirmer = "it recurs with unrelated topics").isComplete)
    }
}

class BackupRoundTripTest {
    @Test
    fun exportThenImportIsLossless() {
        val backup = JournalBackup(
            exportedAt = 1_700_000_000_000,
            contentVersion = 1,
            progress = listOf(
                LevelProgress(
                    "foundations-01", "foundations", LevelStatus.MASTERED, quizScore = 83,
                    quizAnswersJson = Codec.encodeAttempts(listOf(QuizAttempt(1, listOf(2, 3, null, 0, 1, 1), 2, 83))),
                    drillsDone = 0b11111, fieldLogged = true,
                    reflection = "My father folds his arms — always has. “Quotes”, emoji 🙂, newlines\nand all.",
                    masteredAt = 5, nextReviewAt = 6, reviewStreak = 2, lastReviewedAt = 7,
                    fieldNote = Codec.encodeFieldNote(FieldNote(1, "saw", "guess", "would be wrong if", "", 9)),
                    lastOpenedAt = 8,
                ),
                LevelProgress("foundations-11", "foundations", LevelStatus.IN_PROGRESS),
            ),
            bookmarks = listOf(PoseBookmark("p_stand_neutral", "seen at the bus stop", 3)),
            reviews = listOf(ReviewLog(1, "foundations-01", 10, 120_000)),
        )
        val text = ProgressRepository.encode(backup)
        assertEquals(backup, ProgressRepository.decode(text))
    }
}
