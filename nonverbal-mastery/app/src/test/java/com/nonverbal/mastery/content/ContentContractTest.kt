package com.nonverbal.mastery.content

import com.nonverbal.mastery.data.models.ContentPack
import com.nonverbal.mastery.data.models.Level
import com.nonverbal.mastery.data.models.Tier
import com.nonverbal.mastery.data.repo.ContentRepository
import com.nonverbal.mastery.data.repo.Curriculum
import com.nonverbal.mastery.design.pose.ArmPresets
import com.nonverbal.mastery.design.pose.HandShape
import com.nonverbal.mastery.design.pose.LegPresets
import com.nonverbal.mastery.design.pose.PoseSolver
import com.nonverbal.mastery.features.level.levelWordCount
import com.nonverbal.mastery.features.level.pagesFor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream

/** Assembles the pack from /content exactly as the Gradle task does. */
object TestContent {
    private val root = File("../content")
    private val json = Json { ignoreUnknownKeys = true }

    val text: String by lazy {
        val meta = json.parseToJsonElement(File(root, "pack.meta.json").readText()).jsonObject
        val categories = meta.getValue("categories").jsonArray.map { cat ->
            val id = cat.jsonObject.getValue("id").jsonPrimitive.content
            val levels = (File(root, "levels/$id").listFiles { f -> f.extension == "json" } ?: emptyArray())
                .map { json.parseToJsonElement(it.readText()) }
                .sortedBy { it.jsonObject.getValue("index").jsonPrimitive.int }
            JsonObject(cat.jsonObject + ("levels" to JsonArray(levels)))
        }
        val poses = json.parseToJsonElement(File(root, "poses.json").readText())
        buildJsonObject {
            meta.forEach { (k, v) -> put(k, v) }
            put("categories", JsonArray(categories))
            put("poses", poses as JsonElement)
        }.toString()
    }

    val pack: ContentPack by lazy { ContentRepository.decode(text) }
    val curriculum: Curriculum by lazy { Curriculum(pack) }
}

class ContentContractTest {
    private val pack = TestContent.pack
    private val poseIds = pack.poses.map { it.id }.toSet()

    private val WATCH_HOSTS = setOf("youtube.com", "youtu.be", "ted.com", "vimeo.com", "bbc.co.uk", "pbs.org")

    private fun words(s: String?) = s?.split(Regex("\\s+"))?.count { it.isNotBlank() } ?: 0
    private fun sentences(s: String) = s.trim().split(Regex("(?<=[.!?…])\\s+(?=[\\p{Lu}“\"(])")).count { it.isNotBlank() }

    private val problems = mutableListOf<String>()
    private fun check(ok: Boolean, where: String, what: String) { if (!ok) problems += "$where: $what" }

    private fun tierFor(index: Int) = Curriculum.tierFor(index)

    private fun checkLevel(l: Level) {
        val w = "${l.categoryId}/${l.index} ${l.title}"
        check(words(l.hook) in 1..30, w, "hook must be ≤30 words (is ${words(l.hook)})")
        check(words(l.why) in 150..250, w, "why must be 150–250 words (is ${words(l.why)})")
        check(l.teach.size in 5..7, w, "teach must have 5–7 blocks (has ${l.teach.size})")
        l.teach.forEachIndexed { i, b ->
            check(words(b.p) in 200..400, w, "teach[$i] body must be 200–400 words (is ${words(b.p)})")
            check(b.poseId != null && b.poseId in poseIds, w, "teach[$i] must reference a pose in the library (${b.poseId})")
            check(!b.disconfirm.isNullOrBlank(), w, "teach[$i] must state its disconfirming condition")
            check(b.h.isNotBlank() && !b.h.endsWith(":"), w, "teach[$i] heading must be a claim")
            check(b.diagram == null || com.nonverbal.mastery.design.components.DiagramKind.of(b.diagram) != null, w, "teach[$i] diagram '${b.diagram}' is not a known plate")
        }
        check(l.teach.any { it.pullQuote != null }, w, "at least one teach block needs a pull quote")
        check(l.poseGallery.size in 3..6, w, "poseGallery must have 3–6 pairs (has ${l.poseGallery.size})")
        l.poseGallery.forEachIndexed { i, p ->
            check(p.doPoseId in poseIds && p.dontPoseId in poseIds, w, "poseGallery[$i] references a missing pose")
            check(p.doPoseId != p.dontPoseId, w, "poseGallery[$i] compares a pose with itself")
            check(sentences(p.annotation) in 1..2, w, "poseGallery[$i] annotation must be 1–2 sentences")
        }
        check(words(l.caseStudy) in 250..400, w, "caseStudy must be 250–400 words (is ${words(l.caseStudy)})")
        check(l.observe.size in 3..4, w, "observe must have 3–4 assignments")
        l.observe.forEachIndexed { i, o -> check(sentences(o) in 1..3, w, "observe[$i] must be 1–3 sentences (is ${sentences(o)})") }
        check(l.drills.size in 3..5, w, "drills must number 3–5")
        l.drills.forEachIndexed { i, d ->
            check(d.steps.size in 4..6, w, "drills[$i] needs 4–6 steps")
            check(d.minutes in 10..30, w, "drills[$i] must take 10–30 minutes")
        }
        check(l.quiz.size in 5..10, w, "quiz must have 5–10 questions")
        l.quiz.forEachIndexed { i, q ->
            check(q.options.size == 4, w, "quiz[$i] needs 4 options")
            check(q.answer in q.options.indices, w, "quiz[$i] answer out of range")
            check(sentences(q.explain) in 2..4, w, "quiz[$i] explain must be 2–4 sentences (is ${sentences(q.explain)})")
        }
        check(l.quiz.any { it.isMisconceptionTest }, w, "quiz needs a misconception test")
        check(l.quiz.any { it.isScenario }, w, "quiz needs a scenario question")
        check(l.quiz.map { it.answer }.toSet().size >= 3, w, "quiz answers should not cluster on one letter")
        // The right answer must not be findable by length alone: never far longer than the longest
        // wrong option, and the longest option in at most half of a chapter's questions.
        l.quiz.forEachIndexed { i, q ->
            val longestWrong = q.options.filterIndexed { j, _ -> j != q.answer }.maxOf { it.length }
            check(q.options[q.answer].length <= longestWrong * 1.35, w, "quiz[$i] right answer is much longer than every wrong one")
        }
        val longestIsRight = l.quiz.count { q -> q.options[q.answer].length == q.options.maxOf { it.length } }
        check(longestIsRight * 2 <= l.quiz.size, w, "the right answer is the longest option in $longestIsRight of ${l.quiz.size} questions")
        check(words(l.reflection.prompt) in 60..100, w, "reflection prompt must be 60–100 words (is ${words(l.reflection.prompt)})")
        check(l.reflection.prompt.contains("specific", ignoreCase = true) || l.reflection.prompt.contains("one ", ignoreCase = true), w, "reflection prompt must ask for a specific instance")
        check(words(l.reflection.guidance) >= 40, w, "reflection guidance must be a real paragraph")
        check(l.reflection.minCharacters >= 150, w, "reflection minimum must be ≥150 characters")
        check(words(l.failureMode) in 100..200, w, "failureMode must be 100–200 words (is ${words(l.failureMode)})")
        check(l.furtherStudy.size in 2..4, w, "furtherStudy must have 2–4 references")
        // Watch links: official uploads on known video sites, each with something specific to notice.
        check(l.watch.size in 1..4, w, "watch must have 1–4 links")
        l.watch.forEachIndexed { i, v ->
            val host = Regex("^https://([^/]+)/").find(v.url)?.groupValues?.get(1)?.removePrefix("www.")?.removePrefix("m.")
            check(host in WATCH_HOSTS, w, "watch[$i] url must be https on a known video site, got ${v.url}")
            check(v.title.isNotBlank() && v.source.isNotBlank(), w, "watch[$i] needs a title and a source")
            check(words(v.notice) in 8..70, w, "watch[$i] notice must be 8–70 words")
            check(v.at == null || Regex("^\\d{1,2}:\\d{2}(:\\d{2})?$").matches(v.at), w, "watch[$i] at must look like m:ss")
        }
        if (l.moreCases.isNotEmpty() || l.workedReading != null) checkDepth(l, w)
        check(sentences(l.reviewPrompt) in 1..2, w, "reviewPrompt must be 1–2 sentences")
        check(!l.reviewPrompt.trim().endsWith("?"), w, "reviewPrompt must be a task, not a question")
        check(l.tier == tierFor(l.index), w, "tier ${l.tier} does not match index ${l.index}")
        check(l.estimatedMinutes in 30..240, w, "estimatedMinutes looks wrong")
        val prose = listOf(l.why, l.caseStudy, l.failureMode, l.reflection.prompt) + l.teach.flatMap { listOf(it.p, it.disconfirm.orEmpty()) }
        prose.flatMap { it.split(Regex("\\n\\s*\\n")) }.forEach { para ->
            check(para.count { it == '*' } % 2 == 0, w, "unbalanced *italic* markers in: ${para.take(60)}")
        }
        val all = TestContent.text
        check(!Regex("\\bTODO\\b|lorem ipsum|\\bTBD\\b|coming soon", RegexOption.IGNORE_CASE).containsMatchIn(all), "pack", "contains a stub marker")
        prose.forEach { check(!Regex("[a-z]- (?!and |or |to )[a-z]").containsMatchIn(it), w, "word broken by a line-wrap hyphen in: ${it.take(60)}") }
    }

    @Test
    fun everyAuthoredLevelMeetsTheContract() {
        val levels = pack.categories.flatMap { it.levels }
        assertTrue("no levels authored", levels.isNotEmpty())
        levels.forEach(::checkLevel)
        val ids = levels.map { it.id }
        check(ids.size == ids.toSet().size, "pack", "duplicate level ids")
        pack.categories.forEach { c -> c.levels.forEach { check(it.categoryId == c.id, it.id, "categoryId mismatch") } }
        assertTrue("Content contract violations:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun level213IsGatedWhenWritten() {
        val l213 = pack.categories.firstOrNull { it.id == "inner-world" }?.levels?.firstOrNull { it.index == 13 }
        if (l213 != null) {
            assertTrue("2.13 must open through a safety gate", l213.safetyGate != null && l213.safetyGate!!.regions.size >= 5)
        }
        val gate = pack.crisisResources
        assertTrue("crisis resources must ship with the pack", gate != null && gate.regions.any { it.contact.contains("988") } && gate.regions.any { it.contact.contains("116 123") })
    }

    @Test
    fun posesAreValidAndDescribed() {
        fun paramsOk(w: String, pp: com.nonverbal.mastery.data.models.PoseParams) {
            check(pp.armPoseL in ArmPresets.all && pp.armPoseR in ArmPresets.all, w, "unknown arm preset ${pp.armPoseL}/${pp.armPoseR}")
            check(pp.legStanceL in LegPresets.all && pp.legStanceR in LegPresets.all, w, "unknown leg preset ${pp.legStanceL}/${pp.legStanceR}")
            check(pp.handShapeL in HandShape.ids && pp.handShapeR in HandShape.ids, w, "unknown hand shape ${pp.handShapeL}/${pp.handShapeR}")
        }
        pack.poses.forEach { p ->
            val w = "pose ${p.id}"
            check(words(p.desc) >= 25, w, "desc must describe the pose in prose (≥25 words)")
            check(p.title.isNotBlank(), w, "needs a plate title")
            check(p.theme in KNOWN_THEMES, w, "unknown theme '${p.theme}'")
            paramsOk(w, p.params)
            p.companions.forEachIndexed { i, c -> paramsOk("$w companion $i", c.params) }
            check(p.annotations.size <= 4, w, "at most 4 annotations")
            val scene = PoseSolver.solveScene(p)
            p.annotations.forEach { a ->
                check(scene.anchor(a.at) != null, w, "unknown anchor '${a.at}'")
                check(a.color in setOf("accent-warm", "accent-cool", "do-green", "don't-red"), w, "unknown colour '${a.color}'")
            }
            scene.figures.forEach { sk ->
                val pts = listOf(sk.head, sk.left.hand.wrist, sk.right.hand.wrist, sk.left.knee, sk.right.knee, sk.left.foot.toe)
                check(pts.all { !it.x.isNaN() && !it.y.isNaN() && !it.z.isNaN() }, w, "solver produced NaN")
                check(sk.left.foot.toe.y > -1f && sk.right.foot.toe.y > -1f, w, "a foot goes through the floor")
            }
        }
        val ids = pack.poses.map { it.id }
        check(ids.size == ids.toSet().size, "poses", "duplicate pose ids")
        check(pack.poses.size >= 120, "poses", "the library should hold about 120 poses (has ${pack.poses.size})")
        assertTrue("Pose library problems:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    private companion object {
        val KNOWN_THEMES = setOf("stance", "arms", "self-touch", "head", "posture", "gesture", "seated", "walking", "breath",
            "grief", "professional", "greeting", "together")
    }

    /**
     * The depth layer: what turns a 4,000-word chapter into a 10,000-word one without padding.
     * More scenes read two ways, one scene read slowly, more ways it goes wrong, the questions
     * readers ask, and variations on every drill.
     */
    private fun checkDepth(l: Level, w: String) {
        check(l.moreCases.size in 3..4, w, "moreCases must have 3–4 scenes (has ${l.moreCases.size})")
        l.moreCases.forEachIndexed { i, c ->
            check(words(c.title) in 2..12, w, "moreCases[$i] title must be 2–12 words")
            check(words(c.scene) in 200..400, w, "moreCases[$i] scene must be 200–400 words (is ${words(c.scene)})")
            check(words(c.misread) in 60..160, w, "moreCases[$i] misread must be 60–160 words (is ${words(c.misread)})")
            check(words(c.betterRead) in 80..200, w, "moreCases[$i] betterRead must be 80–200 words (is ${words(c.betterRead)})")
        }
        val r = l.workedReading
        check(r != null, w, "a deepened chapter needs a worked reading")
        if (r != null) {
            check(words(r.scene) in 120..300, w, "workedReading scene must be 120–300 words (is ${words(r.scene)})")
            check(r.steps.size in 5..7, w, "workedReading must have 5–7 steps")
            r.steps.forEachIndexed { i, s ->
                check(words(s.label) in 1..6, w, "workedReading step $i label must be 1–6 words")
                check(words(s.text) in 60..220, w, "workedReading step $i must be 60–220 words (is ${words(s.text)})")
            }
        }
        check(l.moreFailures.size in 2..4, w, "moreFailures must have 2–4 entries")
        l.moreFailures.forEachIndexed { i, f ->
            check(words(f.title) in 2..14, w, "moreFailures[$i] title must be 2–14 words")
            check(words(f.body) in 120..260, w, "moreFailures[$i] must be 120–260 words (is ${words(f.body)})")
        }
        check(l.questions.size in 4..6, w, "questions must have 4–6 entries")
        l.questions.forEachIndexed { i, q ->
            check(words(q.q) in 4..40 && q.q.trim().endsWith("?"), w, "questions[$i] must be a real question of ≤40 words")
            check(words(q.a) in 80..240, w, "questions[$i] answer must be 80–240 words (is ${words(q.a)})")
        }
        l.drills.forEachIndexed { i, d ->
            check(d.variations.size in 1..3, w, "drills[$i] needs 1–3 variations")
            d.variations.forEachIndexed { j, v -> check(words(v) in 15..90, w, "drills[$i] variation $j must be 15–90 words") }
        }
        check(l.quiz.size >= 8, w, "a deepened chapter's quiz must have 8–10 questions")
        check(l.poseGallery.size >= 5, w, "a deepened chapter needs 5–6 pose pairs")
        val total = levelWordCount(l)
        check(total >= 9_900, w, "a deepened chapter must reach 22 pages, ≥9,900 words (is $total)")
        val prose = l.moreCases.flatMap { listOf(it.scene, it.misread, it.betterRead) } + l.moreFailures.map { it.body } +
            l.questions.map { it.a } + (r?.steps?.map { it.text } ?: emptyList()) + listOfNotNull(r?.scene)
        prose.forEach { para -> check(para.count { it == '*' } % 2 == 0, w, "unbalanced *italic* markers in: ${para.take(60)}") }
    }

    @Test
    fun outlineCoversNinetyChaptersAndPackFitsBudget() {
        val c = TestContent.curriculum
        assertTrue("six categories expected", pack.categories.size >= 6)
        pack.categories.forEach { assertTrue("${it.id} outline should list its 15 chapters", it.outline.size >= 15) }
        assertTrue("planned total ≥ 90", c.plannedTotal >= 90)
        assertTrue("ethics statement ships", (pack.ethics?.sections?.sumOf { s -> s.paragraphs.sumOf { words(it) } } ?: 0) >= 1000)
        val bytes = TestContent.text.toByteArray()
        val gz = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(bytes) } }.size()
        assertTrue("compressed pack must stay under 2 MB (is $gz bytes)", gz < 2 * 1024 * 1024)

        val levels = pack.categories.flatMap { it.levels }
        val totalWords = levels.sumOf { levelWordCount(it) }
        println("CONTENT REPORT")
        levels.forEach { println("  ${c.reference(it)} ${it.title}: ${levelWordCount(it)} words, ~${pagesFor(levelWordCount(it))} pages, tier ${it.tier}") }
        println("  levels written: ${levels.size} / ${c.plannedTotal}")
        println("  words: $totalWords  pages: ~${pagesFor(totalWords)}  poses: ${pack.poses.size}")
        println("  pack: ${bytes.size} bytes raw, $gz bytes gzipped")
        println("  tiers: " + Tier.entries.joinToString { t -> "$t=${levels.count { it.tier == t }}" })
    }
}
