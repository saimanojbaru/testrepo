package com.nonverbal.mastery.design.pose

import androidx.compose.ui.graphics.Color

enum class HairStyle {
    CROP, SIDE, CURLY, BUZZ, RECEDING, CHILD, BABY,
    /** Women's styles, and a girl's ponytail. */
    LONG, BOB, BUN, PONYTAIL, CURLY_LONG, PIXIE, HIJAB,
    /** A Sikh man's turban. */
    TURBAN,
}
enum class Beard { NONE, STUBBLE, TRIM, FULL }
enum class Top { TSHIRT, SHIRT, JUMPER, POLO, BLOUSE, ONESIE }
enum class Bottom { TROUSERS, SKIRT }
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
    val woman: Boolean = false,
    val bottom: Bottom = Bottom.TROUSERS,
    /** Below a skirt: tights, or bare skin when null. */
    val legColor: Color? = null,
    /** A headscarf's colour, for [HairStyle.HIJAB]. */
    val scarfColor: Color = Color(0xFF5E6C8A),
) {
    enum class Sleeves { SHORT, ROLLED, LONG }

    /** A child of this person: same skin and hair colour, a child's clothes. */
    fun child(index: Int, girl: Boolean = false): Look = copy(
        name = "$name's child",
        hairStyle = if (girl) HairStyle.PONYTAIL else HairStyle.CHILD, beard = Beard.NONE, glasses = false,
        top = Top.TSHIRT, sleeves = Sleeves.SHORT,
        topColor = KidTops[index % KidTops.size], bottomColor = Color(0xFF4F6E92),
        shoeColor = Color(0xFFEDEAE3), soleColor = Color(0xFFB9B3A8),
        hair = if (hair.isGrey()) Color(0xFF3B2A20) else hair,
        build = 1f, age = Age.CHILD, woman = girl, bottom = Bottom.TROUSERS, legColor = null,
    )

    /** A baby of this person: a soft one-piece, bare head with a little hair. */
    fun baby(): Look = copy(
        name = "$name's baby",
        hairStyle = HairStyle.BABY, beard = Beard.NONE, glasses = false,
        top = Top.ONESIE, sleeves = Sleeves.LONG,
        topColor = Color(0xFFF1E4C8), bottomColor = Color(0xFFF1E4C8), shoeColor = Color(0xFFF1E4C8), soleColor = null,
        hair = if (hair.isGrey()) Color(0xFF6A4A33) else hair,
        build = 1f, age = Age.BABY, woman = false, bottom = Bottom.TROUSERS, legColor = null,
    )

    private companion object {
        val KidTops = listOf(Color(0xFFE0A33C), Color(0xFF6E9FC6), Color(0xFFD9705C), Color(0xFF79A27A))
        fun Color.isGrey() = kotlin.math.abs(red - green) < 0.04f && kotlin.math.abs(green - blue) < 0.06f && red > 0.5f
    }
}

/**
 * The people who appear in the figures. Varied on purpose, in skin, age, build and style, so that
 * most readers will find someone like themselves. Each pose is drawn with one of them, chosen
 * from the pose id, so the same pose always shows the same person and a do/don't pair can share him.
 * Main figures are men unless a pose says otherwise; women appear where a pose names them.
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

    val women: List<Look> = listOf(
        Look("Sara", skin = Color(0xFFE9C0A0), hair = DarkBrown, hairStyle = HairStyle.LONG, beard = Beard.NONE,
            top = Top.BLOUSE, topColor = Color(0xFFB9777E), bottomColor = Color(0xFF2F3A52), shoeColor = BlackShoe, woman = true),
        Look("Amara", skin = Color(0xFF5E3A27), hair = Black, hairStyle = HairStyle.CURLY_LONG, beard = Beard.NONE,
            top = Top.JUMPER, topColor = Color(0xFFC79A3E), bottomColor = Color(0xFF3D5372), shoeColor = Trainer,
            soleColor = TrainerSole, woman = true),
        Look("Mei", skin = Color(0xFFEDCBA8), hair = Black, hairStyle = HairStyle.BOB, beard = Beard.NONE,
            top = Top.TSHIRT, topColor = Color(0xFF34466B), bottomColor = Color(0xFF8A8C8F), shoeColor = BlackShoe,
            sleeves = Look.Sleeves.SHORT, woman = true, bottom = Bottom.SKIRT, legColor = Color(0xFF2B2A2E), build = 0.95f),
        Look("Priya", skin = Color(0xFFB8805A), hair = Black, hairStyle = HairStyle.BUN, beard = Beard.NONE,
            top = Top.BLOUSE, topColor = Color(0xFF3C7A74), bottomColor = Color(0xFF2E2E33), shoeColor = BrownShoe, woman = true),
        Look("Claire", skin = Color(0xFFF2D4BE), hair = Blond, hairStyle = HairStyle.PONYTAIL, beard = Beard.NONE,
            top = Top.SHIRT, topColor = Color(0xFFA9C0D6), bottomColor = Color(0xFF2E3A55), shoeColor = BrownShoe,
            sleeves = Look.Sleeves.ROLLED, woman = true),
        Look("Rosa", skin = Color(0xFFC9906A), hair = Brown, hairStyle = HairStyle.LONG, beard = Beard.NONE,
            top = Top.BLOUSE, topColor = Color(0xFF8A3B4A), bottomColor = Color(0xFF8A3B4A), shoeColor = BlackShoe,
            woman = true, bottom = Bottom.SKIRT, legColor = Color(0xFF3A2F2E)),
        Look("Fatima", skin = Color(0xFFC08A62), hair = Black, hairStyle = HairStyle.HIJAB, beard = Beard.NONE,
            top = Top.BLOUSE, topColor = Color(0xFFD8CBB4), bottomColor = Color(0xFF3B3A45), shoeColor = BrownShoe,
            woman = true, scarfColor = Color(0xFF5E6C8A)),
        Look("Margaret", skin = Color(0xFFEDCBB4), hair = White, hairStyle = HairStyle.PIXIE, beard = Beard.NONE,
            top = Top.JUMPER, topColor = Color(0xFF7D8C7A), bottomColor = Color(0xFF5A5650), shoeColor = BrownShoe,
            woman = true, age = Age.OLDER, glasses = true, build = 1.04f),
        Look("Grace", skin = Color(0xFF6E4733), hair = Grey, hairStyle = HairStyle.PIXIE, beard = Beard.NONE,
            top = Top.JUMPER, topColor = Color(0xFF8E7BA0), bottomColor = Color(0xFF3E3B47), shoeColor = BlackShoe,
            woman = true, age = Age.OLDER, build = 1.06f),
    )

    /**
     * Men who appear only where a pose names them, so adding them does not change who is drawn in the
     * poses that already exist.
     */
    val namedMen: List<Look> = listOf(
        Look("Harpreet", skin = Color(0xFFB8835E), hair = Black, hairStyle = HairStyle.TURBAN, beard = Beard.FULL,
            top = Top.SHIRT, topColor = Color(0xFFE3DED3), bottomColor = Color(0xFF34415A), shoeColor = BrownShoe,
            scarfColor = Color(0xFF2F4170), build = 1.04f),
        Look("Tony", skin = Color(0xFFE1B08D), hair = DarkBrown, hairStyle = HairStyle.CROP, beard = Beard.STUBBLE,
            top = Top.POLO, topColor = Color(0xFF6E5A7E), bottomColor = Color(0xFF3B4250), shoeColor = BlackShoe,
            sleeves = Look.Sleeves.SHORT, build = 1.4f),
        Look("Walter", skin = Color(0xFFEAC4A8), hair = White, hairStyle = HairStyle.RECEDING, beard = Beard.NONE,
            top = Top.JUMPER, topColor = Color(0xFF6A7B8C), bottomColor = Color(0xFF4F4A44), shoeColor = BrownShoe,
            age = Age.OLDER, glasses = true, build = 0.95f),
    )

    val everyone: List<Look> get() = men + women + namedMen

    private val olderMen = men.filter { it.age == Age.OLDER }
    private val olderWomen = women.filter { it.age == Age.OLDER }
    private val youngerWomen = women.filter { it.age == Age.ADULT && it.hairStyle != HairStyle.HIJAB }

    private fun hash(s: String): Int {
        var h = 0x811C9DC5.toInt()
        for (ch in s) { h = h xor ch.code; h *= 0x01000193 }
        return h and 0x7FFFFFFF
    }

    /** The main man for a pose. */
    fun main(seed: String): Look = men[hash(seed) % men.size]

    /**
     * Everyone in a scene. [hints] name who each figure is ("woman", "older-woman", "girl", a cast name...);
     * without a hint the first figure is a man chosen from the seed, companions are other men, a small
     * companion becomes a child or baby of the main figure, and a person lying down is an older man.
     */
    fun forScene(seed: String, scene: Scene, hints: List<String?> = emptyList()): List<Look> {
        val h = hash(seed)
        val used = mutableSetOf<String>()
        fun pick(pool: List<Look>, salt: Int): Look {
            var j = (h / 7 + salt * 5) % pool.size
            var tries = 0
            while (pool[j].name in used && tries < pool.size) { j = (j + 1) % pool.size; tries++ }
            return pool[j].also { used += it.name }
        }
        fun byHint(hint: String, salt: Int): Look? = when (hint.lowercase()) {
            "woman" -> pick(youngerWomen, salt)
            "older-woman" -> pick(olderWomen, salt)
            "older-man" -> pick(olderMen, salt)
            "man" -> pick(men.filter { it.age == Age.ADULT }, salt)
            "hijab" -> women.first { it.hairStyle == HairStyle.HIJAB }.also { used += it.name }
            else -> everyone.firstOrNull { it.name.equals(hint, ignoreCase = true) }?.also { used += it.name }
        }
        val firstHint = hints.getOrNull(0)
        val first = firstHint?.let { byHint(it, 0) } ?: main(seed).also { used += it.name }
        var kids = 0
        return scene.figures.mapIndexed { i, sk ->
            if (i == 0) return@mapIndexed first
            val hint = hints.getOrNull(i)
            val k = sk.placement.scale
            when {
                k < 0.45f -> first.baby()
                k < 0.8f -> first.child(kids++, girl = hint == "girl")
                hint != null -> byHint(hint, i) ?: pick(men, i)
                sk.placement.pitch != 0f -> pick(olderMen, i)
                else -> pick(men, i)
            }
        }
    }
}
