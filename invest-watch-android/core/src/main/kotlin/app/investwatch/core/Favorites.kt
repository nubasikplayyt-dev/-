package app.investwatch.core

/**
 * Избранное хранится в config/favorites.json репозитория: агент по нему
 * решает, что мониторить 4 раза в сутки. Приложение пишет этот файл через
 * Contents API с проверкой `sha`, поэтому изменения надо уметь сливать
 * с чужой версией, а не затирать её.
 */
object Favorites {

    /**
     * Ожидающие отправки правки. Звёздочка меняет интерфейс сразу,
     * а сюда кладётся намерение.
     */
    data class Pending(
        val added: Set<String> = emptySet(),
        val removed: Set<String> = emptySet(),
    ) {
        val isEmpty: Boolean get() = added.isEmpty() && removed.isEmpty()

        fun add(assetId: String) = Pending(added + assetId, removed - assetId)

        fun remove(assetId: String) = Pending(added - assetId, removed + assetId)
    }

    /**
     * Сливает локальные правки со свежим содержимым файла из репозитория.
     *
     * Состав списка: удалённое вычитается, добавленное прибавляется.
     * Порядок: сначала локальный (его задаёт перетаскивание на экране),
     * затем те бумаги, которых локально ещё не видели, — в порядке из файла.
     * Так чужая правка не теряется и порядок владельца не сбрасывается.
     */
    fun merge(remote: List<String>, localOrder: List<String>, pending: Pending): List<String> {
        val result = (remote.toSet() + pending.added) - pending.removed

        val ordered = mutableListOf<String>()
        for (id in localOrder) if (id in result && id !in ordered) ordered += id
        for (id in remote) if (id in result && id !in ordered) ordered += id
        for (id in pending.added) if (id in result && id !in ordered) ordered += id

        return ordered
    }

    /** Содержимое файла для коммита. Пояснение агенту сохраняем. */
    fun render(favorites: List<String>, note: String = DEFAULT_NOTE): String {
        val items = favorites.joinToString(",\n") { "    ${quote(it)}" }
        return buildString {
            append("{\n")
            append("  \"note\": ").append(quote(note)).append(",\n")
            append("  \"favorites\": [")
            if (favorites.isEmpty()) append("]") else append("\n").append(items).append("\n  ]")
            append("\n}\n")
        }
    }

    private fun quote(value: String): String = buildString {
        append('"')
        for (char in value) when (char) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            else -> append(char)
        }
        append('"')
    }

    const val DEFAULT_NOTE: String =
        "Избранное владельца. Пишется Android-приложением; агент читает его, " +
            "чтобы знать, что мониторить 4 раза в сутки."
}
