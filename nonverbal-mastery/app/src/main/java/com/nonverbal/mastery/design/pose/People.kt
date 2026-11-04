package com.nonverbal.mastery.design.pose

import androidx.compose.ui.graphics.Color

enum class HairStyle { CROP, SIDE, CURLY, BUZZ, RECEDING, CHILD, BABY }
enum class Beard { NONE, STUBBLE, TRIM, FULL }
enum class Top { TSHIRT, SHIRT, JUMPER, POLO, ONESIE }
enum class Age { BABY, CHILD, ADULT, OLDER }

/**
 * What one person in a picture looks like: skin, hair, beard, clothes and build. The pose
 * system decides where the body is; a Look decides who it is.
 */
data class Look(
    val name: String,
    val skin: Color,
    val hair: Color,
    val hairStyle: HairStyle,
    val beard: Beard,
    val top: Top,
    val topColor: Color,
    val bottomColor: Color,
    val shoeColor: Color,
    /** Short sleeves show the forearm; long sleeves reach the wrist; rolled stop below the elbow. */
    val sleeves: Sleeves = Sleeves.LONG,
    /** 1 is average. Above 1 is broader and heavier, below 1 slighter. */
    val build: Float = 1f,
    val age: Age = Age.ADULT,
    val glasses: Boolean = false,
    /** Sole colour, for shoes whose sole contrasts (trainers). */
    val soleColor: Color? = null,
) {
    enum class Sleeves { SHORT, ROLLED, LONG }

    /** A child of this person: same skin and hair colour, a child's clothes. */
    fun child(index: Int): Look = copy(
        name = "$name's child",
        hairStyle = HairStyle.CHILD, beard = Beard.NONE, glasses = false,
        top = Top.TSHIRT, sleeves = Sleeves.SHORT,
        topColor = KidTops[index % KidTops.size], bottomColor = Color(0xFF4F6E92),
        shoeColor = Color(0xFFEDEAE3), soleColor = Color(0xFFB9B3A8),
        hair = if (hair.isGrey()) Color(0xFF3B2A20) else hair,
        build = 1f, age = Age.CHILD,
    )

    /** A baby of this person: a soft one-piece, bare head with a little hair. */
    fun baby(): Look = copy(
        name = "$name's baby",
        hairStyle = HairStyle.BABY, beard = Beard.NONE, glasses = false,
        top = Top.ONESIE, sleeves = Sleeves.LONG,
        topColor = Color(0xFFF1E4C8), bottomColor = Color(0xFFF1E4C8), shoeColor = Color(0xFFF1E4C8), soleColor = null,
        hair = if (hair.isGrey()) Color(0xFF6A4A33) else hair,
        build = 1f, age = Age.BABY,
    )

    private companion object {
        val KidTops = listOf(Color(0xFFE0A33C), Color(0xFF6E9FC6), Color(0xFFD9705C), Color(0xFF79A27A))
        fun Color.isGrey() = kotlin.math.abs(red - green) < 0.04f && kotlin.math.abs(green - blue) < 0.06f && red > 0.5f
    }
}

/**
 * The men who appear in the figures. Varied on purpose, in skin, age, build and style, so that
 * most readers will find someone like themselves. Each pose is drawn with one of them, chosen
 * from the pose id, so the same pose always shows the same man and a do/don't pair can share him.
 */
object Cast {
    private val Black = Color(0xFF1C1714)
    private val DarkBrown = Color(0xFF3A281E)
    private val Brown = Color(0xFF5E4130)
    private val Blond = Color(0xFFB28C5C)
    private val Auburn = Color(0xFF7D4227)
    private val Grey = Color(0xFFA9A49D)
    private val White = Color(0xFFD9D5CE)

    private val BrownShoe = Color(0xFF4A3223)
    private val BlackShoe = Color(0xFF242325)
    private val Trainer = Color(0xFFEDEAE3)
    private val TrainerSole = Color(0xFFB9B3A8)

    val men: List<Look> = listOf(
        Look("Tom", skin = Color(0xFFEBC3A2), hair = DarkBrown, hairStyle = HairStyle.SIDE, beard = Beard.NONE,
            top = Top.SHIRT, topColor = Color(0xFF9FB6CD), bottomColor = Color(0xFF3E4553), shoeColor = BrownShoe),
        Look("Kwame", skin = Color(0xFF6A422C), hair = Black, hairStyle = HairStyle.BUZZ, beard = Beard.TRIM,
            top = Top.TSHIRT, topColor = Color(0xFFB65E3C), bottomColor = Color(0xFF2E3549), shoeColor = Trainer,
            soleColor = TrainerSole, sleeves = Look.Sleeves.SHORT, build = 1.04f),
        Look("Arjun", skin = Color(0xFFBE835C), hair = Black, hairStyle = HairStyle.SIDE, beard = Beard.FULL,
            top = Top.POLO, topColor = Color(0xFF2F5160), bottomColor = Color(0xFFA6916F), shoeColor = BrownShoe,
            sleeves = Look.Sleeves.SHORT, build = 1.08f),
        Look("Ben", skin = Color(0xFFF1D2BC), hair = Blond, hairStyle = HairStyle.CROP, beard = Beard.STUBBLE,
            top = Top.JUMPER, topColor = Color(0xFF66765A), bottomColor = Color(0xFF41597A), shoeColor = Trainer,
            soleColor = TrainerSole, build = 0.96f),
        Look("Hassan", skin = Color(0xFFB27650), hair = DarkBrown, hairStyle = HairStyle.CURLY, beard = Beard.FULL,
            top = Top.SHIRT, topColor = Color(0xFF4F6F90), bottomColor = Color(0xFF2B2B2E), shoeColor = BrownShoe,
            sleeves = Look.Sleeves.ROLLED),
        Look("Greg", skin = Color(0xFFE6BD9B), hair = Grey, hairStyle = HairStyle.RECEDING, beard = Beard.TRIM,
            top = Top.JUMPER, topColor = Color(0xFF7C3E43), bottomColor = Color(0xFF5B5247), shoeColor = BrownShoe,
            build = 1.1f, age = Age.OLDER, glasses = true),
        Look("Kenji", skin = Color(0xFFE8C59F), hair = Black, hairStyle = HairStyle.CROP, beard = Beard.NONE,
            top = Top.TSHIRT, topColor = Color(0xFFC09445), bottomColor = Color(0xFF33475F), shoeColor = Trainer,
            soleColor = TrainerSole, sleeves = Look.Sleeves.SHORT, build = 0.93f),
        Look("Marcus", skin = Color(0xFF4F3222), hair = Black, hairStyle = HairStyle.CURLY, beard = Beard.STUBBLE,
            top = Top.SHIRT, topColor = Color(0xFFE6E1D6), bottomColor = Color(0xFF2C3247), shoeColor = BlackShoe,
            sleeves = Look.Sleeves.ROLLED, build = 1.02f),
        Look("Joseph", skin = Color(0xFF714A34), hair = White, hairStyle = HairStyle.BUZZ, beard = Beard.TRIM,
            top = Top.JUMPER, topColor = Color(0xFF384B62), bottomColor = Color(0xFF8C7A5E), shoeColor = BrownShoe,
            build = 1.06f, age = Age.OLDER, glasses = true),
        Look("Callum", skin = Color(0xFFF0CDB2), hair = Auburn, hairStyle = HairStyle.CROP, beard = Beard.STUBBLE,
            top = Top.TSHIRT, topColor = Color(0xFF3F5E4C), bottomColor = Color(0xFF4A6688), shoeColor = Trainer,
            soleColor = TrainerSole, sleeves = Look.Sleeves.SHORT),
        Look("Diego", skin = Color(0xFFCE9A72), hair = Brown, hairStyle = HairStyle.SIDE, beard = Beard.STUBBLE,
            top = Top.POLO, topColor = Color(0xFF8E9DAE), bottomColor = Color(0xFF3B3A3F), shoeColor = BlackShoe,
            sleeves = Look.Sleeves.SHORT, build = 1.0f),
        Look("Sam", skin = Color(0xFF8F5C3E), hair = Black, hairStyle = HairStyle.CURLY, beard = Beard.NONE,
            top = Top.JUMPER, topColor = Color(0xFFB7A47E), bottomColor = Color(0xFF2E3A4D), shoeColor = BlackShoe,
            build = 0.98f),
    )

    private val older = men.filter { it.age == Age.OLDER }

    private fun hash(s: String): Int {
        var h = 0x811C9DC5.toInt()
        for (ch in s) { h = h xor ch.code; h *= 0x01000193 }
        return h and 0x7FFFFFFF
    }

    /** The main man for a pose. */
    fun main(seed: String): Look = men[hash(seed) % men.size]

    /**
     * Everyone in a scene. The first figure is the pose's own man. Companions are other men, never
     * the same one twice; a small companion becomes a child or a baby of the main figure; a person
     * lying down (the bedside) is an older man.
     */
    fun forScene(seed: String, scene: Scene): List<Look> {
        val first = main(seed)
        val used = mutableSetOf(first.name)
        val h = hash(seed)
        var kids = 0
        return scene.figures.mapIndexed { i, sk ->
            if (i == 0) return@mapIndexed first
            val k = sk.placement.scale
            when {
                k < 0.45f -> first.baby()
                k < 0.8f -> first.child(kids++)
                sk.placement.pitch != 0f -> older.firstOrNull { it.name !in used }?.also { used += it.name } ?: older.first()
                else -> {
                    var j = (h / 7 + i * 5) % men.size
                    while (men[j].name in used) j = (j + 1) % men.size
                    men[j].also { used += it.name }
                }
            }
        }
    }
}
