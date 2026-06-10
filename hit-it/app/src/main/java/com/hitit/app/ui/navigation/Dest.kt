package com.hitit.app.ui.navigation

/** Navigation routes for VibeOS. */
object Dest {
    const val TODAY = "today"
    const val REPS = "reps"
    const val HITS = "hits"
    const val GRID = "grid"
    const val PROFILE = "profile"
    const val BODYFLOW = "bodyflow"
    const val MONEYVIBE = "moneyvibe"

    const val LOCK_IN = "lock_in"
    const val CHECK_IN = "check_in"
    const val BIG_PLAYS = "big_plays"
    const val COACH = "coach"
    const val SHADOW = "shadow_self"
    const val YOGA = "yoga"
    const val POSE_STUDIO = "pose_studio?poseId={poseId}"

    const val REP_DETAIL = "rep_detail/{repId}"
    const val REP_EDIT = "rep_edit?repId={repId}"
    const val HIT_EDIT = "hit_edit?taskId={taskId}"
    const val BIG_PLAY_DETAIL = "big_play_detail/{planId}"
    const val BIG_PLAY_EDIT = "big_play_edit?planId={planId}"
    const val LOCKER = "locker?noteId={noteId}"

    const val ARG_REP_ID = "repId"
    const val NEW_REP_ID = -1L
    const val ARG_TASK_ID = "taskId"
    const val NEW_TASK_ID = -1L
    const val ARG_PLAN_ID = "planId"
    const val NEW_PLAN_ID = -1L
    const val ARG_NOTE_ID = "noteId"
    const val ROOT_NOTE_ID = -1L
    const val ARG_POSE_ID = "poseId"
    const val NEW_POSE_ID = -1L

    fun repDetail(id: Long): String = "rep_detail/$id"
    fun repEdit(id: Long? = null): String = "rep_edit?repId=${id ?: NEW_REP_ID}"
    fun hitEdit(id: Long? = null): String = "hit_edit?taskId=${id ?: NEW_TASK_ID}"
    fun bigPlayDetail(id: Long): String = "big_play_detail/$id"
    fun bigPlayEdit(id: Long? = null): String = "big_play_edit?planId=${id ?: NEW_PLAN_ID}"
    fun locker(noteId: Long? = null): String = "locker?noteId=${noteId ?: ROOT_NOTE_ID}"
    fun poseStudio(poseId: Long? = null): String = "pose_studio?poseId=${poseId ?: NEW_POSE_ID}"
}
