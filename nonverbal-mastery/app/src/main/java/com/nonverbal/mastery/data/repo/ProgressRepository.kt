package com.nonverbal.mastery.data.repo

import androidx.room.withTransaction
import com.nonverbal.mastery.data.db.AppDatabase
import com.nonverbal.mastery.data.db.LevelProgress
import com.nonverbal.mastery.data.db.LevelStatus
import com.nonverbal.mastery.data.db.PoseBookmark
import com.nonverbal.mastery.data.db.ReviewLog
import com.nonverbal.mastery.data.models.Level
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

/** Everything the reader has done, as one portable document. */
@Serializable
data class JournalBackup(
    val app: String = APP_TAG,
    val schema: Int = 1,
    val exportedAt: Long,
    val contentVersion: Int,
    val progress: List<LevelProgress>,
    val bookmarks: List<PoseBookmark>,
    val reviews: List<ReviewLog>,
) {
    companion object { const val APP_TAG = "nonverbal-mastery" }
}

class ProgressRepository(
    private val db: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val progressDao = db.progressDao()
    private val reviewDao = db.reviewDao()
    private val bookmarkDao = db.poseBookmarkDao()
    private val writeLock = Mutex()

    val progress: Flow<Map<String, LevelProgress>> = progressDao.observeAll().map { list -> list.associateBy { it.levelId } }
    val reviews: Flow<List<ReviewLog>> = reviewDao.observeAll()
    val bookmarks: Flow<List<PoseBookmark>> = bookmarkDao.observeAll()

    private suspend fun edit(level: Level, block: (LevelProgress) -> LevelProgress) = writeLock.withLock {
        val current = progressDao.get(level.id) ?: LevelProgress(level.id, level.categoryId, LevelStatus.OPEN)
        var next = block(current)
        if (next.status == LevelStatus.OPEN && next != current) next = next.copy(status = LevelStatus.IN_PROGRESS)
        progressDao.upsert(next)
    }

    suspend fun markOpened(level: Level) = edit(level) { it.copy(lastOpenedAt = clock()) }

    suspend fun setDrill(level: Level, index: Int, done: Boolean) = edit(level) {
        val bit = 1 shl index
        it.copy(drillsDone = if (done) it.drillsDone or bit else it.drillsDone and bit.inv())
    }

    /** Records an answer. The first answer to a question in an attempt is final for that attempt. */
    suspend fun answer(level: Level, questionIndex: Int, choice: Int) = edit(level) { p ->
        val attempts = Codec.attempts(p).toMutableList()
        val now = clock()
        var current = attempts.lastOrNull()?.takeIf { it.completedAt == null }
            ?: QuizAttempt(startedAt = now, answers = List(level.quiz.size) { null }).also { attempts += it }
        if (current.answers.getOrNull(questionIndex) != null) return@edit p
        val answers = current.answers.toMutableList().apply {
            while (size < level.quiz.size) add(null)
            this[questionIndex] = choice
        }
        current = current.copy(answers = answers)
        if (answers.all { it != null }) current = current.copy(completedAt = now, score = scoreOf(level, answers))
        attempts[attempts.lastIndex] = current
        p.copy(
            quizAnswersJson = Codec.encodeAttempts(attempts),
            quizScore = current.score ?: p.quizScore,
        )
    }

    /** Starts a fresh attempt; earlier attempts stay in the history. */
    suspend fun retakeQuiz(level: Level) = edit(level) { p ->
        val attempts = Codec.attempts(p) + QuizAttempt(startedAt = clock(), answers = List(level.quiz.size) { null })
        p.copy(quizAnswersJson = Codec.encodeAttempts(attempts))
    }

    suspend fun saveReflection(level: Level, text: String) = edit(level) { it.copy(reflection = text) }

    suspend fun saveFieldNote(level: Level, note: FieldNote) = edit(level) {
        val logged = note.isComplete
        it.copy(
            fieldNote = Codec.encodeFieldNote(if (logged && note.loggedAt == null) note.copy(loggedAt = clock()) else note),
            fieldLogged = logged,
        )
    }

    /** Masters the level if, and only if, every criterion is met. Returns whether it did. */
    suspend fun master(level: Level): Boolean {
        var done = false
        edit(level) { p ->
            if (!MasteryCheck.of(level, p).ready || p.status == LevelStatus.MASTERED) return@edit p
            done = true
            val now = clock()
            ReviewScheduler.onMastered(p.copy(status = LevelStatus.MASTERED, masteredAt = now), now)
        }
        return done
    }

    suspend fun completeReview(level: Level, durationMs: Long) {
        writeLock.withLock {
            val p = progressDao.get(level.id) ?: return
            val now = clock()
            db.withTransaction {
                progressDao.upsert(ReviewScheduler.onReviewed(p, now))
                reviewDao.insert(ReviewLog(levelId = level.id, reviewedAt = now, durationMs = durationMs))
            }
        }
    }

    suspend fun toggleBookmark(poseId: String, on: Boolean) {
        if (on) bookmarkDao.upsert(PoseBookmark(poseId, savedAt = clock())) else bookmarkDao.delete(poseId)
    }

    suspend fun saveBookmarkNote(poseId: String, note: String) {
        val existing = bookmarkDao.getAll().firstOrNull { it.poseId == poseId }
        bookmarkDao.upsert((existing ?: PoseBookmark(poseId, savedAt = clock())).copy(note = note))
    }

    suspend fun exportJournal(contentVersion: Int): JournalBackup = JournalBackup(
        exportedAt = clock(),
        contentVersion = contentVersion,
        progress = progressDao.getAll().sortedBy { it.levelId },
        bookmarks = bookmarkDao.getAll().sortedBy { it.poseId },
        reviews = reviewDao.getAll(),
    )

    /** Replaces everything with the backup's contents, in one transaction. */
    suspend fun importJournal(backup: JournalBackup) {
        require(backup.app == JournalBackup.APP_TAG) { "Not a Nonverbal Mastery journal" }
        writeLock.withLock {
            db.withTransaction {
                progressDao.clear(); bookmarkDao.clear(); reviewDao.clear()
                progressDao.insertAll(backup.progress)
                bookmarkDao.insertAll(backup.bookmarks)
                reviewDao.insertAll(backup.reviews)
            }
        }
    }

    suspend fun resetAll() = writeLock.withLock {
        db.withTransaction { progressDao.clear(); bookmarkDao.clear(); reviewDao.clear() }
    }

    companion object {
        val backupJson = kotlinx.serialization.json.Json {
            prettyPrint = true
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

        fun encode(backup: JournalBackup): String = backupJson.encodeToString(JournalBackup.serializer(), backup)
        fun decode(text: String): JournalBackup = backupJson.decodeFromString(JournalBackup.serializer(), text)
    }
}
