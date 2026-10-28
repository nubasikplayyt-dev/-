package app.lingvo.cloud

import android.util.Log
import app.lingvo.core.Competition
import app.lingvo.core.LeaderboardPeriod
import app.lingvo.core.ShareCodes
import app.lingvo.core.SharedProgress
import app.lingvo.core.Standing
import app.lingvo.data.FriendDao
import app.lingvo.data.FriendEntity
import app.lingvo.data.LingvoRepository
import app.lingvo.data.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/** Друг с его последней известной сводкой (может быть не самой свежей — обновляется по кнопке). */
data class Friend(val code: String, val progress: SharedProgress, val addedAt: Long, val fetchedAt: Long)

/** Входящий запрос «добавь меня в ответ». */
data class FriendRequest(val code: String, val nick: String, val at: Long)

/**
 * Обмен прогрессом по коду через Firebase Realtime Database.
 *
 * Каждый публикует свою сводку по адресу `/shares/<свой код>`: ник и счётчики, без слов и заметок.
 * Друг, которому вы дали код, читает этот узел. Чтобы перестать делиться с кем-то, код меняется —
 * старый узел удаляется и становится недействительным.
 */
class FriendsRepository(
    private val cloud: Cloud,
    private val dao: FriendDao,
    private val settings: SettingsStore,
    private val repo: LingvoRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private fun shares() = cloud.database?.getReference("shares")

    fun observeFriends(): Flow<List<Friend>> = dao.observeAll().map { list ->
        list.mapNotNull { e -> SharedProgress.fromMap(e.toMap())?.let { Friend(e.code, it, e.addedAt, e.fetchedAt) } }
    }

    /** Мой код: создаётся при первом обращении. */
    suspend fun myCode(): String {
        settings.current().shareCode.takeIf { it.isNotBlank() }?.let { return it }
        val code = ShareCodes.generate()
        settings.update { it.copy(shareCode = code) }
        return code
    }

    /** Моя сводка — то же, что видят друзья. */
    suspend fun mySummary(): SharedProgress {
        val s = settings.current()
        val courses = repo.courses().map { "${it.flag} ${it.name}" }
        return Competition.summarize(
            nick = s.nick.ifBlank { Competition.defaultNick() },
            days = repo.days(),
            today = repo.today(),
            snapshot = repo.snapshot(),
            streak = repo.streak().current,
            courses = courses,
            privacy = s.privacy(),
            now = clock(),
        )
    }

    /** Отправляет мою сводку в облако. Возвращает текст ошибки или null при успехе. */
    suspend fun publish(): String? {
        val s = settings.current()
        if (!s.friendsEnabled) return null
        val ref = shares() ?: return Cloud.NOT_CONFIGURED
        cloud.signInAnonymously()?.let { if (cloud.account.value == null) return it }
        return try {
            ref.child(myCode()).updateChildren(mySummary().toMap()).await()
            settings.update { it.copy(sharePublishedAt = clock()) }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось отправить сводку", e)
            e.localizedMessage ?: "Нет связи"
        }
    }

    /** Новый код вместо старого: старый узел удаляется, прежние друзья больше ничего не видят. */
    suspend fun rotateCode(): String? {
        val ref = shares() ?: return Cloud.NOT_CONFIGURED
        val old = settings.current().shareCode
        return try {
            if (old.isNotBlank()) ref.child(old).removeValue().await()
            settings.update { it.copy(shareCode = ShareCodes.generate()) }
            publish()
        } catch (e: Exception) {
            e.localizedMessage ?: "Нет связи"
        }
    }

    /** Убирает мою сводку из облака (при выключении режима друзей). */
    suspend fun unpublish() {
        val code = settings.current().shareCode
        if (code.isNotBlank()) runCatching { shares()?.child(code)?.removeValue()?.await() }
    }

    private suspend fun read(code: String): SharedProgress? {
        val snapshot = shares()?.child(code)?.get()?.await() ?: return null
        @Suppress("UNCHECKED_CAST")
        val map = snapshot.value as? Map<String, Any?> ?: return null
        return SharedProgress.fromMap(map)
    }

    /**
     * Добавляет друга по коду или ссылке-приглашению. Заодно оставляет ему запрос,
     * чтобы он мог добавить вас в ответ (подтверждение с его стороны).
     */
    suspend fun addFriend(raw: String): String? {
        val code = ShareCodes.fromLink(raw) ?: return "Код не распознан. Он выглядит так: ABCD-EFGH"
        if (code == settings.current().shareCode) return "Это ваш собственный код"
        if (shares() == null) return Cloud.NOT_CONFIGURED
        cloud.signInAnonymously()?.let { if (cloud.account.value == null) return it }
        return try {
            val progress = read(code) ?: return "По этому коду никто не делится прогрессом"
            val now = clock()
            dao.upsert(FriendEntity.from(code, progress, addedAt = now, fetchedAt = now))
            // Запрос «добавь меня в ответ» — друг увидит его у себя и решит сам.
            val me = mySummary()
            runCatching {
                shares()!!.child(code).child("requests").child(myCode())
                    .setValue(mapOf("nick" to me.nick, "at" to now)).await()
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось добавить друга", e)
            e.localizedMessage ?: "Нет связи"
        }
    }

    /** Входящие запросы: кто-то добавил вас и ждёт ответного добавления. */
    suspend fun incomingRequests(): List<FriendRequest> {
        val code = settings.current().shareCode.takeIf { it.isNotBlank() } ?: return emptyList()
        val known = dao.all().map { it.code }.toSet()
        return runCatching {
            val snapshot = shares()?.child(code)?.child("requests")?.get()?.await() ?: return emptyList()
            snapshot.children.mapNotNull { child ->
                val c = child.key ?: return@mapNotNull null
                if (c in known) return@mapNotNull null
                @Suppress("UNCHECKED_CAST")
                val m = child.value as? Map<String, Any?> ?: return@mapNotNull null
                FriendRequest(c, (m["nick"] as? String).orEmpty().ifBlank { "Без имени" }, (m["at"] as? Number)?.toLong() ?: 0L)
            }
        }.getOrElse { emptyList() }
    }

    suspend fun dismissRequest(code: String) {
        val mine = settings.current().shareCode
        if (mine.isNotBlank()) runCatching { shares()?.child(mine)?.child("requests")?.child(code)?.removeValue()?.await() }
    }

    /** Обновляет сводки всех друзей и отправляет свою. Возвращает ошибку или null. */
    suspend fun refreshAll(): String? {
        if (shares() == null) return Cloud.NOT_CONFIGURED
        cloud.signInAnonymously()?.let { if (cloud.account.value == null) return it }
        val publishError = publish()
        var error: String? = null
        for (friend in dao.all()) {
            try {
                val progress = read(friend.code)
                if (progress != null) dao.upsert(FriendEntity.from(friend.code, progress, friend.addedAt, clock()))
            } catch (e: Exception) {
                error = e.localizedMessage ?: "Нет связи"
            }
        }
        return publishError ?: error
    }

    suspend fun removeFriend(code: String) = dao.delete(code)

    /** Данные друзей не хранятся дольше, чем нужно: старые сводки удаляются. */
    suspend fun forgetStale(maxAgeMs: Long = 30L * 24 * 3600 * 1000) {
        val limit = clock() - maxAgeMs
        dao.all().filter { it.fetchedAt < limit }.forEach { dao.delete(it.code) }
    }

    suspend fun leaderboard(period: LeaderboardPeriod): List<Standing> {
        val friends = dao.all().mapNotNull { e -> SharedProgress.fromMap(e.toMap())?.let { e.code to it } }.toMap()
        return Competition.leaderboard(mySummary(), friends, period)
    }

    companion object {
        private const val TAG = "FriendsRepository"
    }
}
