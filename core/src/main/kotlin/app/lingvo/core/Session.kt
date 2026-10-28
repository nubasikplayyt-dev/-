package app.lingvo.core

/**
 * Очередь активной сессии повторения. Хранится в базе после каждого ответа,
 * поэтому сессию можно продолжить после закрытия приложения.
 */
data class SessionProgress(
    val queue: List<String>,
    val done: Int,
    val total: Int,
) {
    val remaining: Int get() = queue.size
    val isFinished: Boolean get() = queue.isEmpty()
    val fraction: Float get() = if (total == 0) 1f else done.toFloat() / total

    /**
     * Учитывает ответ на текущую карточку. Если карточка снова должна появиться в ближайшие
     * [learnAheadMs] (ошибка или шаг изучения), она возвращается в конец очереди.
     */
    fun answer(cardId: String, nextDue: Long, now: Long, learnAheadMs: Long = LEARN_AHEAD_MS): SessionProgress {
        val rest = queue.toMutableList()
        if (!rest.remove(cardId)) return this
        val again = nextDue <= now + learnAheadMs
        return if (again) {
            rest += cardId
            copy(queue = rest, done = done + 1, total = total + 1)
        } else {
            copy(queue = rest, done = done + 1)
        }
    }

    /** Убирает из очереди удалённые или приостановленные карточки. */
    fun retainOnly(existing: Set<String>): SessionProgress {
        val filtered = queue.filter { it in existing }
        val removed = queue.size - filtered.size
        return copy(queue = filtered, total = (total - removed).coerceAtLeast(done))
    }

    companion object {
        const val LEARN_AHEAD_MS = 20 * 60 * 1000L

        fun start(cardIds: List<String>) = SessionProgress(cardIds.distinct(), 0, cardIds.distinct().size)

        fun encodeQueue(ids: List<String>): String = ids.joinToString("\n")
        fun decodeQueue(text: String): List<String> = text.split('\n').filter { it.isNotBlank() }
    }
}
