package app.lingvo.core

/**
 * Реальные показатели пользователя, из которых вычисляются достижения, области карты,
 * существа и иконки. Всё считается по данным базы — ничего не «накручивается».
 */
data class ProgressSnapshot(
    val answers: Int = 0,
    val remembered: Int = 0,
    val sessions: Int = 0,
    val wordsAdded: Int = 0,
    val wordsLearned: Int = 0,
    val mastered: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val goalDays: Int = 0,
    val courses: Int = 0,
    val longestSession: Int = 0,
    val perfectSessions: Int = 0,
    val listens: Int = 0,
    val regions: Int = 0,
    val creatures: Int = 0,
    val achievements: Int = 0,
)

enum class Metric(val labelRu: String, val value: (ProgressSnapshot) -> Int) {
    ANSWERS("ответов", { it.answers }),
    REMEMBERED("вспомненных слов", { it.remembered }),
    SESSIONS("завершённых занятий", { it.sessions }),
    WORDS_ADDED("слов в словарях", { it.wordsAdded }),
    WORDS_LEARNED("хорошо запомненных слов", { it.wordsLearned }),
    MASTERED("устойчиво усвоенных слов", { it.mastered }),
    STREAK("дней серии", { it.bestStreak }),
    GOAL_DAYS("дней с выполненной целью", { it.goalDays }),
    COURSES("изучаемых языковых курсов", { it.courses }),
    LONGEST_SESSION("ответов за одно занятие", { it.longestSession }),
    PERFECT_SESSIONS("занятий без ошибок", { it.perfectSessions }),
    LISTENS("прослушиваний произношения", { it.listens }),
    REGIONS("открытых областей", { it.regions }),
    CREATURES("существ в коллекции", { it.creatures }),
    ACHIEVEMENTS("достижений", { it.achievements }),
}

data class Requirement(val metric: Metric, val target: Int) {
    fun value(s: ProgressSnapshot) = metric.value(s)
    fun met(s: ProgressSnapshot) = value(s) >= target
    fun progress(s: ProgressSnapshot): Float = (value(s).toFloat() / target).coerceIn(0f, 1f)
    fun describe(): String = "$target ${metric.labelRu}"
}

enum class UnlockKind { ACHIEVEMENT, REGION, CREATURE, ICON }

data class UnlockKey(val kind: UnlockKind, val id: String)

// ---------------------------------------------------------------- Достижения

enum class AchievementCategory(val labelRu: String) {
    LEARNING("Обучение"),
    REGULARITY("Регулярность"),
    VOCABULARY("Словарный запас"),
    EXPLORATION("Исследование мира"),
    COLLECTION("Коллекционирование"),
}

data class AchievementDef(
    val id: String,
    val title: String,
    val description: String,
    val category: AchievementCategory,
    val requirement: Requirement,
    /** Иллюстрация: ID иконки-картинки (art_<id>) или null — тогда рисуется эмблема с [symbol]. */
    val art: String?,
    val symbol: String,
    /** Награда — альтернативная иконка приложения (ID из [AppIcons]) или null. */
    val rewardIcon: String? = null,
)

object Achievements {
    val all: List<AchievementDef> = listOf(
        // Обучение
        AchievementDef("first_step", "Первый шаг", "Завершите первое занятие", AchievementCategory.LEARNING,
            Requirement(Metric.SESSIONS, 1), "open_book", "👣", rewardIcon = "open_book"),
        AchievementDef("hundred", "Сотня ответов", "Ответьте на 100 карточек", AchievementCategory.LEARNING,
            Requirement(Metric.ANSWERS, 100), null, "💯"),
        AchievementDef("marathon", "Словесный марафон", "Дайте 50 ответов за одно занятие", AchievementCategory.LEARNING,
            Requirement(Metric.LONGEST_SESSION, 50), "dialog_star", "🏃", rewardIcon = "dialog_star"),
        AchievementDef("bullseye", "В яблочко", "Завершите занятие из 10+ карточек без единой ошибки", AchievementCategory.LEARNING,
            Requirement(Metric.PERFECT_SESSIONS, 1), "bullseye", "🎯", rewardIcon = "bullseye"),
        AchievementDef("sniper", "Снайпер", "Проведите 10 занятий без ошибок", AchievementCategory.LEARNING,
            Requirement(Metric.PERFECT_SESSIONS, 10), "sniper", "🏹", rewardIcon = "sniper"),
        AchievementDef("listener", "Чуткое ухо", "Прослушайте произношение 50 раз", AchievementCategory.LEARNING,
            Requirement(Metric.LISTENS, 50), "listener", "🎧", rewardIcon = "listener"),
        AchievementDef("keeper", "Хранитель знаний", "Вспомните слова 1000 раз", AchievementCategory.LEARNING,
            Requirement(Metric.REMEMBERED, 1000), "knowledge_cup", "🏆", rewardIcon = "knowledge_cup"),
        AchievementDef("repeat_master", "Мастер повторения", "Вспомните слова 5000 раз", AchievementCategory.LEARNING,
            Requirement(Metric.REMEMBERED, 5000), null, "🧠"),
        // Регулярность
        AchievementDef("first_goal", "Цель дня", "Впервые выполните дневную цель", AchievementCategory.REGULARITY,
            Requirement(Metric.GOAL_DAYS, 1), null, "✅"),
        AchievementDef("plan", "Точно по плану", "Выполните дневную цель 10 раз", AchievementCategory.REGULARITY,
            Requirement(Metric.GOAL_DAYS, 10), "plan", "📅", rewardIcon = "plan"),
        AchievementDef("week", "Неделя без пауз", "Серия из 7 дней", AchievementCategory.REGULARITY,
            Requirement(Metric.STREAK, 7), "week7", "⏱️", rewardIcon = "week7"),
        AchievementDef("two_weeks", "Две недели огня", "Серия из 14 дней", AchievementCategory.REGULARITY,
            Requirement(Metric.STREAK, 14), "days14", "🔥", rewardIcon = "days14"),
        AchievementDef("nonstop", "Без остановки", "Серия из 30 дней", AchievementCategory.REGULARITY,
            Requirement(Metric.STREAK, 30), "days30", "🗓️", rewardIcon = "days30"),
        AchievementDef("hundred_days", "Сто рассветов", "Серия из 100 дней", AchievementCategory.REGULARITY,
            Requirement(Metric.STREAK, 100), null, "🌅"),
        // Словарный запас
        AchievementDef("collector", "Коллекционер слов", "Соберите 100 слов в словарях", AchievementCategory.VOCABULARY,
            Requirement(Metric.WORDS_ADDED, 100), "words100", "💜", rewardIcon = "words100"),
        AchievementDef("chronicler", "Летописец", "Соберите 300 слов в словарях", AchievementCategory.VOCABULARY,
            Requirement(Metric.WORDS_ADDED, 300), "chronicle", "✍️", rewardIcon = "chronicle"),
        AchievementDef("new_height", "Новая высота", "50 слов хорошо запоминаются", AchievementCategory.VOCABULARY,
            Requirement(Metric.WORDS_LEARNED, 50), "level_up", "⛰️", rewardIcon = "level_up"),
        AchievementDef("graduate", "Выпускник", "100 слов устойчиво усвоены", AchievementCategory.VOCABULARY,
            Requirement(Metric.MASTERED, 100), "graduate", "🎓", rewardIcon = "graduate"),
        // Исследование мира
        AchievementDef("pathfinder", "Следопыт", "Откройте 3 области карты", AchievementCategory.EXPLORATION,
            Requirement(Metric.REGIONS, 3), null, "🧭"),
        AchievementDef("horizons", "Покоритель горизонтов", "Изучайте два языковых курса", AchievementCategory.EXPLORATION,
            Requirement(Metric.COURSES, 2), "horizons", "🌍", rewardIcon = "horizons"),
        AchievementDef("polyglot", "Полиглот", "Изучайте три языковых курса", AchievementCategory.EXPLORATION,
            Requirement(Metric.COURSES, 3), null, "🗺️"),
        AchievementDef("world_lord", "Повелитель миров", "Откройте все области карты", AchievementCategory.EXPLORATION,
            Requirement(Metric.REGIONS, 8), "crown_world", "👑", rewardIcon = "crown_world"),
        // Коллекционирование
        AchievementDef("friends", "Друг существ", "Соберите 3 существ-полиглотов", AchievementCategory.COLLECTION,
            Requirement(Metric.CREATURES, 3), null, "🐾"),
        AchievementDef("menagerie", "Хранитель зверинца", "Соберите 6 существ-полиглотов", AchievementCategory.COLLECTION,
            Requirement(Metric.CREATURES, 6), null, "🦄"),
        AchievementDef("champion", "Чемпион", "Получите 15 достижений", AchievementCategory.COLLECTION,
            Requirement(Metric.ACHIEVEMENTS, 15), "champion", "🥇", rewardIcon = "champion"),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id }
}

// ---------------------------------------------------------------- Карта языкового мира

data class RegionDef(
    val id: String,
    val name: String,
    val description: String,
    /** null — стартовая область, открыта сразу. Области открываются по порядку. */
    val requirement: Requirement?,
    val symbol: String,
    val colorA: Long,
    val colorB: Long,
    /** Находка, которую пользователь получает при открытии области. */
    val itemName: String,
    val itemSymbol: String,
    /** Положение на карте (доли ширины и высоты). */
    val x: Float,
    val y: Float,
)

object WorldMap {
    val regions: List<RegionDef> = listOf(
        RegionDef("meadow", "Поляна первых слов", "Здесь начинается путешествие: первые слова прорастают, как цветы.",
            null, "🌼", 0xFF7BD389, 0xFFFFE66D, "Карманный разговорник", "📒", 0.18f, 0.88f),
        RegionDef("grove", "Роща памяти", "Деревья хранят каждое вспомненное слово и шелестят им при ветре.",
            Requirement(Metric.REMEMBERED, 25), "🌳", 0xFF2EC4B6, 0xFF8BE38B, "Ключ памяти", "🗝️", 0.62f, 0.78f),
        RegionDef("bridge", "Мост привычки", "Мост вырастает на одну доску с каждым днём выполненной цели.",
            Requirement(Metric.GOAL_DAYS, 3), "🌉", 0xFFFF9F1C, 0xFFFFD166, "Фонарь путника", "🏮", 0.30f, 0.66f),
        RegionDef("city", "Город словарей", "Улицы из книжных полок, где у каждого слова есть свой дом.",
            Requirement(Metric.WORDS_ADDED, 100), "🏰", 0xFF5E60CE, 0xFF64DFDF, "Библиотечная карточка", "🎫", 0.70f, 0.55f),
        RegionDef("archipelago", "Архипелаг наречий", "Острова, где на каждом берегу говорят на своём языке.",
            Requirement(Metric.COURSES, 2), "🏝️", 0xFF00B4D8, 0xFF90E0EF, "Компас полиглота", "🧭", 0.24f, 0.42f),
        RegionDef("peaks", "Пики мастерства", "Только устойчиво усвоенные слова выдерживают горный ветер.",
            Requirement(Metric.MASTERED, 30), "🏔️", 0xFF4361EE, 0xFFB8C0FF, "Флаг вершины", "🚩", 0.68f, 0.31f),
        RegionDef("ruins", "Руины древних алфавитов", "Забытые письмена оживают перед упорными исследователями.",
            Requirement(Metric.STREAK, 21), "🏛️", 0xFFB5838D, 0xFFFFCDB2, "Обломок скрижали", "🪨", 0.30f, 0.19f),
        RegionDef("library", "Звёздная библиотека", "Обсерватория, где из слов складываются созвездия.",
            Requirement(Metric.REMEMBERED, 2000), "🔭", 0xFF7209B7, 0xFF4CC9F0, "Звёздный атлас", "🌌", 0.66f, 0.08f),
    )

    fun byId(id: String) = regions.firstOrNull { it.id == id }
}

// ---------------------------------------------------------------- Существа-полиглоты

enum class Rarity(val labelRu: String, val color: Long) {
    COMMON("Обычное", 0xFF7BD389),
    RARE("Редкое", 0xFF4CC9F0),
    EPIC("Эпическое", 0xFF9D4EDD),
    LEGENDARY("Легендарное", 0xFFFFB703),
}

/** Форма тела — для векторной отрисовки существа в приложении. */
enum class CreatureShape { BLOB, DROP, FLAME, ORB, STAR, CLOUD, BOOK, CRYSTAL }

data class CreatureDef(
    val id: String,
    val name: String,
    /** Какой аспект обучения символизирует. */
    val aspect: String,
    val description: String,
    val rarity: Rarity,
    val requirements: List<Requirement>,
    /** Развитие: уровень растёт, когда метрика достигает порогов. Уровень 1 — сразу после получения. */
    val evolutionMetric: Metric,
    val evolutionThresholds: List<Int>,
    val stageNames: List<String>,
    val shape: CreatureShape,
    val colorA: Long,
    val colorB: Long,
    /** Область карты, где существо живёт. */
    val homeRegion: String,
) {
    val maxLevel: Int get() = evolutionThresholds.size + 1
    fun level(s: ProgressSnapshot): Int = 1 + evolutionThresholds.count { evolutionMetric.value(s) >= it }
    fun nextThreshold(level: Int): Int? = evolutionThresholds.getOrNull(level - 1)
    fun stageName(level: Int): String = stageNames.getOrElse(level - 1) { stageNames.last() }
}

object Creatures {
    val all: List<CreatureDef> = listOf(
        CreatureDef("lingvik", "Лингвик", "Первые шаги",
            "Пушистый проводник, который появляется у каждого, кто начал путь. Радуется любому новому слову.",
            Rarity.COMMON, listOf(Requirement(Metric.SESSIONS, 1)), Metric.ANSWERS, listOf(200, 1500),
            listOf("Птенец", "Знаток", "Наставник"), CreatureShape.BLOB, 0xFFFFB4A2, 0xFFFF6B6B, "meadow"),
        CreatureDef("mnemo", "Мнемо", "Память",
            "Хранит вспомненные слова в светящихся каплях. Чем больше вспоминаете, тем ярче мерцает.",
            Rarity.RARE, listOf(Requirement(Metric.REMEMBERED, 100)), Metric.REMEMBERED, listOf(600, 3000),
            listOf("Капля", "Ручей", "Озеро памяти"), CreatureShape.DROP, 0xFF4CC9F0, 0xFF4361EE, "grove"),
        CreatureDef("flaro", "Фларо", "Регулярность",
            "Огонёк, который живёт благодаря ежедневным занятиям. Каждый день серии подбрасывает в него искру.",
            Rarity.RARE, listOf(Requirement(Metric.STREAK, 3)), Metric.STREAK, listOf(14, 60),
            listOf("Искра", "Пламя", "Феникс"), CreatureShape.FLAME, 0xFFFFD166, 0xFFFF6B35, "bridge"),
        CreatureDef("kyuri", "Кьюри", "Любознательность",
            "Собирает новые слова, как ракушки, и прячет их в бездонные карманы.",
            Rarity.COMMON, listOf(Requirement(Metric.WORDS_ADDED, 60)), Metric.WORDS_ADDED, listOf(250, 800),
            listOf("Почемучка", "Собиратель", "Энциклопедист"), CreatureShape.CLOUD, 0xFFC8B6FF, 0xFF9381FF, "city"),
        CreatureDef("zippy", "Зиппи", "Скорость освоения",
            "Молния на лапках: появляется после длинных занятий и обожает марафоны.",
            Rarity.RARE, listOf(Requirement(Metric.LONGEST_SESSION, 30)), Metric.LONGEST_SESSION, listOf(60, 120),
            listOf("Шустрик", "Вихрь", "Комета"), CreatureShape.STAR, 0xFFFFE66D, 0xFFFFB703, "bridge"),
        CreatureDef("globi", "Глоби", "Исследование",
            "Маленькая планета-путешественница. Появляется в новых областях карты раньше всех.",
            Rarity.EPIC, listOf(Requirement(Metric.REGIONS, 3)), Metric.REGIONS, listOf(5, 8),
            listOf("Спутник", "Планета", "Галактика"), CreatureShape.ORB, 0xFF80ED99, 0xFF00B4D8, "archipelago"),
        CreatureDef("archivus", "Архивус", "Мастерство повторения",
            "Мудрый книжный страж. Помнит каждое устойчиво усвоенное слово.",
            Rarity.EPIC, listOf(Requirement(Metric.WORDS_LEARNED, 100)), Metric.MASTERED, listOf(150, 500),
            listOf("Свиток", "Фолиант", "Великий архив"), CreatureShape.BOOK, 0xFFE9C46A, 0xFFF4A261, "city"),
        CreatureDef("babeli", "Вавилия", "Новые языки",
            "Кристальное создание, которое звучит на всех языках сразу. Приходит к настоящим полиглотам.",
            Rarity.LEGENDARY, listOf(Requirement(Metric.COURSES, 3), Requirement(Metric.ACHIEVEMENTS, 12)),
            Metric.ACHIEVEMENTS, listOf(18, 24), listOf("Осколок", "Призма", "Звучащий кристалл"),
            CreatureShape.CRYSTAL, 0xFFF72585, 0xFF7209B7, "ruins"),
        CreatureDef("nebula", "Небула", "Путь к звёздам",
            "Хранительница Звёздной библиотеки. Появляется лишь у тех, кто открыл весь мир.",
            Rarity.LEGENDARY, listOf(Requirement(Metric.REGIONS, 8)), Metric.STREAK, listOf(100, 365),
            listOf("Туманность", "Созвездие", "Сверхновая"), CreatureShape.STAR, 0xFF4CC9F0, 0xFF7209B7, "library"),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id }
}

// ---------------------------------------------------------------- Иконки приложения

data class AppIconDef(
    val id: String,
    val name: String,
    val collection: String,
    /** Достижение, открывающее иконку; null — доступна сразу. */
    val achievementId: String?,
)

object AppIcons {
    const val DEFAULT = "world"

    val all: List<AppIconDef> by lazy {
        listOf(
            AppIconDef(DEFAULT, "Lingvo World", "Основная", null),
            AppIconDef("classic", "Классика", "Основная", null),
        ) + Achievements.all.mapNotNull { a ->
            a.rewardIcon?.let { AppIconDef(it, a.title, collectionOf(a.category), a.id) }
        }
    }

    private fun collectionOf(c: AchievementCategory) = when (c) {
        AchievementCategory.REGULARITY -> "Путешествия во времени"
        AchievementCategory.EXPLORATION -> "Исследование мира"
        AchievementCategory.VOCABULARY -> "Знания"
        AchievementCategory.COLLECTION -> "Сокровищница"
        AchievementCategory.LEARNING -> "Магия слов"
    }

    fun byId(id: String) = all.firstOrNull { it.id == id }
}

// ---------------------------------------------------------------- Движок разблокировок

data class UnlockEvent(val key: UnlockKey, val level: Int)

data class Evaluation(
    val snapshot: ProgressSnapshot,
    /** Новые разблокировки (в порядке получения). */
    val unlocked: List<UnlockEvent>,
    /** Существа, повысившие уровень: ID → новый уровень. */
    val levelUps: Map<String, Int>,
)

object GameEngine {
    /**
     * Вычисляет, что пользователь открыл. Цепочки (достижение → область → существо → достижение)
     * разрешаются до устойчивого состояния. Уже полученное никогда не отнимается.
     * @param owned уже открытое: ключ → сохранённый уровень (для существ).
     */
    fun evaluate(base: ProgressSnapshot, owned: Map<UnlockKey, Int>): Evaluation {
        val have = LinkedHashMap(owned)
        val events = ArrayList<UnlockEvent>()
        fun count(kind: UnlockKind) = have.keys.count { it.kind == kind }
        fun snap() = base.copy(
            regions = count(UnlockKind.REGION),
            creatures = count(UnlockKind.CREATURE),
            achievements = count(UnlockKind.ACHIEVEMENT),
        )
        fun add(kind: UnlockKind, id: String, level: Int = 1): Boolean {
            val key = UnlockKey(kind, id)
            if (key in have) return false
            have[key] = level
            events += UnlockEvent(key, level)
            return true
        }

        var changed = true
        var guard = 0
        while (changed && guard++ < 20) {
            changed = false
            var s = snap()
            for (r in WorldMap.regions) {
                if (UnlockKey(UnlockKind.REGION, r.id) in have) continue
                // Области открываются по порядку: следующая требует предыдущую.
                if (r.requirement == null || r.requirement.met(s)) {
                    changed = add(UnlockKind.REGION, r.id) || changed
                    s = snap()
                } else break
            }
            for (a in Achievements.all) {
                if (a.requirement.met(s) && add(UnlockKind.ACHIEVEMENT, a.id)) { changed = true; s = snap() }
            }
            for (c in Creatures.all) {
                if (c.requirements.all { it.met(s) } && add(UnlockKind.CREATURE, c.id, c.level(s))) { changed = true; s = snap() }
            }
            for (icon in AppIcons.all) {
                val ok = icon.achievementId == null || UnlockKey(UnlockKind.ACHIEVEMENT, icon.achievementId) in have
                if (ok && add(UnlockKind.ICON, icon.id)) changed = true
            }
        }
        val finalSnap = snap()
        val levelUps = HashMap<String, Int>()
        for (c in Creatures.all) {
            val key = UnlockKey(UnlockKind.CREATURE, c.id)
            val stored = have[key] ?: continue
            val level = c.level(finalSnap)
            if (level > stored) {
                have[key] = level
                if (events.none { it.key == key }) levelUps[c.id] = level
            }
        }
        val unlockedWithLevels = events.map { e ->
            if (e.key.kind == UnlockKind.CREATURE) e.copy(level = have[e.key] ?: e.level) else e
        }
        return Evaluation(finalSnap, unlockedWithLevels, levelUps)
    }

    /** Ближайшие неполученные достижения — с наибольшим прогрессом. */
    fun nearestAchievements(s: ProgressSnapshot, owned: Set<UnlockKey>, limit: Int = 3): List<AchievementDef> =
        Achievements.all
            .filter { UnlockKey(UnlockKind.ACHIEVEMENT, it.id) !in owned }
            .sortedByDescending { it.requirement.progress(s) }
            .take(limit)

    /** Следующая закрытая область карты и прогресс к ней. */
    fun nextRegion(owned: Set<UnlockKey>): RegionDef? =
        WorldMap.regions.firstOrNull { UnlockKey(UnlockKind.REGION, it.id) !in owned }

    /** Текущая (последняя открытая) область. */
    fun currentRegion(owned: Set<UnlockKey>): RegionDef =
        WorldMap.regions.lastOrNull { UnlockKey(UnlockKind.REGION, it.id) in owned } ?: WorldMap.regions.first()
}
