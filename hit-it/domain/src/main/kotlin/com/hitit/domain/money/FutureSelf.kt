package com.hitit.domain.money

import kotlin.math.pow

/**
 * Future Self — the two timelines. Projects this week's fun-money burn forward: the broke timeline
 * (a year of burning) vs the glow-up timeline (same money compounding monthly instead). Pure math +
 * tiered roast/glow copy, fully tested. Default growth ~12%/yr (long-run index-ish), stated in copy.
 */
data class FutureSelfVision(
    val yearlyBurnPaise: Long,
    val futureValuePaise: Long,
    val years: Int,
    val brokeLine: String,
    val glowLine: String,
)

object FutureSelf {
    const val DEFAULT_YEARS = 5
    const val DEFAULT_ANNUAL_RATE = 0.12

    /** Future value of investing the same weekly amount monthly at [annualRate], compounded monthly. */
    fun futureValuePaise(weeklyBurnPaise: Long, years: Int = DEFAULT_YEARS, annualRate: Double = DEFAULT_ANNUAL_RATE): Long {
        if (weeklyBurnPaise <= 0) return 0
        val monthly = weeklyBurnPaise * 52.0 / 12.0
        val i = annualRate / 12.0
        val n = years * 12
        val fv = monthly * (((1 + i).pow(n) - 1) / i)
        return fv.toLong()
    }

    fun vision(weeklyBurnPaise: Long, years: Int = DEFAULT_YEARS): FutureSelfVision {
        val yearly = weeklyBurnPaise * 52
        val fv = futureValuePaise(weeklyBurnPaise, years)
        val yearlyR = yearly / 100
        val fvR = fv / 100
        val broke = when {
            yearlyR >= 100_000 -> "₹$yearlyR/yr torched. Future you is checking the fridge like it owes them money. 😵"
            yearlyR >= 25_000 -> "₹$yearlyR a year up in vibes. That's a whole trip you keep cancelling. 🫠"
            yearlyR > 0 -> "₹$yearlyR/yr on impulse. Not fatal — but the void says thanks. 🕳️"
            else -> "Zero burn logged. The demon starves. 😇"
        }
        val glow = when {
            fvR >= 200_000 -> "Same money compounding ${years}y ≈ ₹$fvR. Certified glow-up arc. 🤑✨"
            fvR > 0 -> "Redirect it for ${years}y at ~12% and that's ≈ ₹$fvR. Future you, glowing. ✨"
            else -> "Nothing to project yet. Log spends and the timelines appear."
        }
        return FutureSelfVision(yearly, fv, years, broke, glow)
    }
}
