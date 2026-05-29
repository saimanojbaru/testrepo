package com.hitit.app.ui.navigation

/** Navigation routes for "Hit it". */
object Dest {
    const val TODAY = "today"
    const val REPS = "reps"
    const val GRID = "grid"
    const val PROFILE = "profile"

    const val REP_DETAIL = "rep_detail/{repId}"
    const val REP_EDIT = "rep_edit?repId={repId}"

    const val ARG_REP_ID = "repId"
    const val NEW_REP_ID = -1L

    fun repDetail(id: Long): String = "rep_detail/$id"
    fun repEdit(id: Long? = null): String = "rep_edit?repId=${id ?: NEW_REP_ID}"
}
