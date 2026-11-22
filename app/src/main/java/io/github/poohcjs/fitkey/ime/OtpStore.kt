package io.github.poohcjs.fitkey.ime

/** 가장 최근 인증번호 (숫자만, 5분 동안). 알림 글 전체는 저장하지 않는다 */
object OtpStore {
    data class Entry(val code: String, val from: String, val at: Long)

    @Volatile private var latest: Entry? = null
    private val listeners = mutableListOf<() -> Unit>()

    fun put(code: String, from: String, now: Long = System.currentTimeMillis()) {
        latest = Entry(code, from, now)
        listeners.toList().forEach { it() }
    }

    fun fresh(now: Long = System.currentTimeMillis()): Entry? = latest?.takeIf { now - it.at < VALID_MS }

    /** 한 번 넣었으면 지운다 */
    fun consume() { latest = null; listeners.toList().forEach { it() } }

    fun addListener(l: () -> Unit) { listeners += l }
    fun removeListener(l: () -> Unit) { listeners -= l }

    const val VALID_MS = 5 * 60_000L
}
