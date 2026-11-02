package com.nonverbal.mastery.data.seed

import com.nonverbal.mastery.data.db.AppDatabase
import com.nonverbal.mastery.data.db.LevelProgress
import com.nonverbal.mastery.data.db.LevelStatus
import com.nonverbal.mastery.data.repo.Curriculum

/**
 * First-run population. Opens the first written chapter of every category, so a reader can
 * begin anywhere: inner work, social, professional, identity or method — not only foundations.
 * Idempotent; it never touches a level that already has progress.
 */
object SeedData {
    suspend fun firstRun(db: AppDatabase, curriculum: Curriculum) {
        val dao = db.progressDao()
        for (category in curriculum.categories) {
            val first = curriculum.levelsInOrder(category.id).firstOrNull() ?: continue
            if (dao.get(first.id) == null) dao.upsert(LevelProgress(first.id, first.categoryId, LevelStatus.OPEN))
        }
    }
}
