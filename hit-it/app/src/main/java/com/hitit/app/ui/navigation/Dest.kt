package com.hitit.app.ui.navigation

/** Navigation routes for "Hit it". */
object Dest {
    const val TODAY = "today"
    const val REPS = "reps"
    const val HITS = "hits"
    const val GRID = "grid"
    const val PROFILE = "profile"

    const val LOCK_IN = "lock_in"
    const val CHECK_IN = "check_in"

    const val REP_DETAIL = "rep_detail/{repId}"
    const val REP_EDIT = "rep_edit?repId={repId}"
    const val HIT_EDIT = "hit_edit?taskId={taskId}"

    const val ARG_REP_ID = "repId"
    const val NEW_REP_ID = -1L
    const val ARG_TASK_ID = "taskId"
    const val NEW_TASK_ID = -1L

    fun repDetail(id: Long): String = "rep_detail/$id"
    fun repEdit(id: Long? = null): String = "rep_edit?repId=${id ?: NEW_REP_ID}"
    fun hitEdit(id: Long? = null): String = "hit_edit?taskId=${id ?: NEW_TASK_ID}"
}
