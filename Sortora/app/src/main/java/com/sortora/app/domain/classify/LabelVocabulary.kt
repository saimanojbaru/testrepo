package com.sortora.app.domain.classify

import com.sortora.app.domain.model.ContentCategory
import com.sortora.app.domain.model.DetectedLabel

/**
 * Hand-curated knowledge that turns raw model labels (ML Kit's ~450 general labels plus the
 * object detector's coarse classes) into categories, tags and keyword matches.
 *
 * Everything here is plain data: extend these lists to teach Sortora new concepts.
 * All entries are lower-case and singular.
 */
object LabelVocabulary {

    /** Labels that are evidence for a built-in scene category. */
    val categoryLabels: Map<ContentCategory, Set<String>> = mapOf(
        ContentCategory.FOOD to setOf(
            "food", "dish", "cuisine", "meal", "dessert", "cake", "pizza", "sushi", "bread", "fruit",
            "vegetable", "drink", "coffee", "cheeseburger", "hamburger", "burger", "salad", "soup", "noodle",
            "pasta", "baked good", "breakfast", "lunch", "dinner", "cookie", "wine", "beer", "cocktail",
            "juice", "tea", "ice cream", "chocolate", "sandwich", "bento", "seafood", "meat", "egg",
            "pastry", "fast food", "produce", "snack", "recipe", "tableware", "plate",
        ),
        ContentCategory.PET to setOf(
            "dog", "cat", "pet", "bird", "animal", "puppy", "kitten", "fish", "horse", "rabbit", "hamster",
            "butterfly", "insect", "reptile", "cattle", "sheep", "duck", "turtle", "squirrel", "zoo",
            "dinosaur", "bear", "lion", "tiger", "elephant", "monkey", "goat", "chicken", "pony",
        ),
        ContentCategory.LANDSCAPE to setOf(
            "sky", "cloud", "mountain", "beach", "sea", "ocean", "lake", "river", "sunset", "sunrise",
            "forest", "nature", "field", "grass", "snow", "desert", "waterfall", "landscape", "hill",
            "park", "garden", "flower", "coast", "island", "canyon", "rainbow", "star", "moon", "horizon",
            "rock", "cliff", "valley", "jungle", "iceberg", "glacier", "lightning", "plant", "tree",
            "leaf", "storm", "wave", "dune", "marsh", "pond", "volcano", "aurora",
        ),
        ContentCategory.CITY to setOf(
            "building", "skyscraper", "city", "street", "road", "bridge", "tower", "architecture", "house",
            "church", "temple", "monument", "landmark", "castle", "downtown", "alley", "metro",
            "train station", "harbor", "pier", "statue", "fountain", "skyline", "place", "stadium",
            "museum", "mosque", "palace",
        ),
        ContentCategory.PRODUCT to setOf(
            "shoe", "bag", "handbag", "watch", "bottle", "toy", "jewellery", "jewelry", "sunglasses",
            "glasses", "cosmetic", "perfume", "gadget", "mobile phone", "laptop", "computer", "headphone",
            "camera", "furniture", "chair", "lamp", "cup", "mug", "product", "box", "package", "clothing",
            "shirt", "jacket", "dress", "hat", "sneaker", "guitar", "fashion good", "home good",
            "fashion accessory", "lipstick", "ring", "earring", "necklace", "backpack", "wallet",
        ),
        ContentCategory.DOCUMENT to setOf(
            "paper", "document", "receipt", "menu", "poster", "text", "handwriting", "book", "newspaper",
            "letter", "invoice", "whiteboard", "blackboard", "ticket", "card", "notebook", "page",
            "chart", "diagram", "font", "comic", "brochure", "magazine",
        ),
    )

    /** Labels too vague or too anatomical to be useful in a file name. */
    private val genericLabels = setOf(
        "font", "pattern", "rectangle", "circle", "darkness", "light", "tints and shade",
        "material property", "event", "fun", "happiness", "leisure", "pleasure", "cool", "love",
        "flesh", "skin", "eyelash", "eyebrow", "hand", "finger", "nail", "lip", "hair", "beard",
        "moustache", "ear", "nose", "chin", "forehead", "cheek", "jaw", "neck", "tooth", "gesture",
        "sitting", "standing", "smile", "photograph", "image", "picture", "selfie", "screenshot",
        "fashion good", "home good", "place", "art", "design", "style", "electric blue", "magenta",
        "symmetry", "close up", "monochrome", "black and white",
    )

    /** Nicer name forms for some labels. */
    private val tagAliases = mapOf(
        "mobile phone" to "phone",
        "cell phone" to "phone",
        "interior design" to "interior",
        "musical instrument" to "instrument",
        "baked good" to "bakery",
        "fast food" to "fastfood",
        "personal computer" to "computer",
        "computer monitor" to "monitor",
        "motor vehicle" to "vehicle",
        "human face" to "face",
    )

    /**
     * Concepts users commonly type as folder names, expanded into words the models actually emit
     * (and, where it fits, a built-in category). Keys are singular normalised phrases.
     */
    val concepts: Map<String, Concept> = buildMap {
        fun put(keys: List<String>, words: Set<String>, vararg categories: ContentCategory) {
            val concept = Concept(words, categories.toSet())
            keys.forEach { put(it, concept) }
        }
        val food = categoryLabels.getValue(ContentCategory.FOOD)
        val pets = categoryLabels.getValue(ContentCategory.PET)
        val nature = categoryLabels.getValue(ContentCategory.LANDSCAPE)
        val city = categoryLabels.getValue(ContentCategory.CITY)
        val products = categoryLabels.getValue(ContentCategory.PRODUCT)
        val docs = categoryLabels.getValue(ContentCategory.DOCUMENT)

        put(listOf("food", "recipe", "meal", "restaurant", "cooking", "foodie"), food, ContentCategory.FOOD)
        put(listOf("pet", "animal", "wildlife"), pets, ContentCategory.PET)
        put(listOf("dog", "puppy", "doggo"), setOf("dog", "puppy"))
        put(listOf("cat", "kitten", "kitty"), setOf("cat", "kitten"))
        put(listOf("nature", "outdoor", "hiking", "landscape", "scenery"), nature, ContentCategory.LANDSCAPE)
        put(listOf("travel", "vacation", "trip", "holiday", "journey"), nature + city + setOf("landmark", "luggage", "airport", "airplane", "hotel"), ContentCategory.LANDSCAPE, ContentCategory.CITY)
        put(listOf("beach", "sea", "ocean"), setOf("beach", "sea", "ocean", "coast", "wave", "sand", "surfing"))
        put(listOf("mountain", "hike"), setOf("mountain", "hill", "cliff", "valley", "rock", "glacier", "snow"))
        put(listOf("city", "urban", "architecture", "building", "street"), city, ContentCategory.CITY)
        put(listOf("receipt", "bill", "invoice", "expense", "tax", "finance"), setOf("receipt", "invoice", "total", "subtotal", "tax", "amount", "paid", "payment", "bill", "balance", "vat", "cash", "change due", "order"))
        put(listOf("document", "doc", "scan", "paper", "note", "paperwork"), docs, ContentCategory.DOCUMENT)
        put(listOf("whiteboard", "lecture", "class", "school", "study"), setOf("whiteboard", "blackboard", "handwriting", "notebook", "book", "diagram", "chart"), ContentCategory.DOCUMENT)
        put(listOf("work", "office", "job", "meeting"), setOf("desk", "computer", "monitor", "office", "laptop", "whiteboard", "keyboard", "meeting", "presentation"))
        put(listOf("car", "vehicle", "auto", "motorbike", "bike"), setOf("car", "vehicle", "wheel", "tire", "motorcycle", "bicycle", "truck", "bus", "van", "automotive", "motor vehicle"))
        put(listOf("product", "shop", "shopping", "ecommerce", "store", "merch", "listing"), products, ContentCategory.PRODUCT)
        put(listOf("fashion", "clothe", "clothing", "outfit", "style"), setOf("clothing", "shirt", "jacket", "dress", "hat", "shoe", "sneaker", "jeans", "fashion accessory", "handbag"))
        put(listOf("people", "family", "friend", "portrait", "person"), setOf("person", "people", "crowd", "team", "child", "baby", "smile"), ContentCategory.PEOPLE, ContentCategory.GROUP, ContentCategory.SELFIE)
        put(listOf("group", "group photo", "crowd", "team"), setOf("crowd", "team", "people"), ContentCategory.GROUP)
        put(listOf("selfie", "me"), setOf("selfie"), ContentCategory.SELFIE)
        put(listOf("kid", "child", "baby", "children", "toddler"), setOf("child", "baby", "toddler", "toy", "playground"))
        put(listOf("sport", "fitness", "gym", "workout", "training"), setOf("sport", "ball", "gym", "running", "stadium", "football", "soccer", "basketball", "tennis", "swimming", "cycling", "skateboard", "yoga", "racket", "jersey"))
        put(listOf("event", "party", "wedding", "concert", "birthday", "celebration", "festival"), setOf("party", "wedding", "event", "concert", "stage", "cake", "balloon", "crowd", "celebration", "bride", "fireworks", "christmas"))
        put(listOf("art", "painting", "drawing", "museum"), setOf("painting", "art", "drawing", "sculpture", "museum", "statue", "illustration", "comic"))
        put(listOf("music", "band", "instrument"), setOf("guitar", "piano", "musical instrument", "concert", "microphone", "drum", "stage"))
        put(listOf("home", "interior", "house", "room", "decor"), setOf("room", "furniture", "kitchen", "bed", "couch", "interior design", "house", "lamp", "shelf"))
        put(listOf("flower", "plant", "garden", "gardening"), setOf("flower", "plant", "garden", "petal", "tree", "leaf", "houseplant"))
        put(listOf("screenshot", "screen", "screengrab", "capture"), emptySet(), ContentCategory.SCREENSHOT)
        put(listOf("screen recording", "screenrecording", "screencast", "recording"), emptySet(), ContentCategory.SCREEN_RECORDING)
        put(listOf("talking head", "talkinghead", "a roll", "aroll", "interview", "vlog", "podcast", "presenter"), setOf("person"), ContentCategory.TALKING_HEAD)
        put(listOf("b roll", "broll", "footage", "cutaway", "stock"), emptySet(), ContentCategory.B_ROLL)
        put(listOf("meme", "comic", "funny"), setOf("comic", "cartoon", "text", "font"))
    }

    data class Concept(val words: Set<String>, val categories: Set<ContentCategory>)

    // The lists above are written in readable form; lookups compare stemmed phrases on both sides
    // (e.g. "sunglasses" -> "sunglass", "tints and shades" -> "tint shade").
    private val stemmedCategoryLabels: Map<ContentCategory, Set<String>> by lazy {
        categoryLabels.mapValues { (_, words) -> words.map(::stemPhrase).toSet() }
    }
    private val stemmedGeneric: Set<String> by lazy { genericLabels.map(::stemPhrase).toSet() }
    private val stemmedAliases: Map<String, String> by lazy { tagAliases.mapKeys { stemPhrase(it.key) } }

    /** Sum of confidences of labels that belong to each scene category. */
    fun categoryScores(labels: List<DetectedLabel>): Map<ContentCategory, Float> {
        val scores = mutableMapOf<ContentCategory, Float>()
        for (label in labels) {
            val stem = stemPhrase(label.text)
            for ((category, vocabulary) in stemmedCategoryLabels) {
                if (stem in vocabulary) {
                    scores[category] = (scores[category] ?: 0f) + label.confidence
                }
            }
        }
        return scores
    }

    /**
     * Converts a model label into a file-name tag, or null if it carries no useful meaning.
     * Returned tags are lower-case words separated by single spaces.
     */
    fun tagFor(label: String): String? {
        val stem = stemPhrase(label)
        if (stem.isEmpty() || stem in stemmedGeneric) return null
        return stemmedAliases[stem] ?: stem
    }

    /** Normalised + singularised phrase, e.g. "Baked goods" → "baked good". */
    fun stemPhrase(text: String): String = TextNormalizer.stems(text).joinToString(" ")
}
