package pf.tiai.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Un proche à prévenir. */
data class Contact(val name: String, val relation: String, val phone: String)

/** Tout ce que l'application garde en mémoire sur le téléphone. */
class Store(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("tiai", Context.MODE_PRIVATE)

    var userName: String
        get() = p.getString("userName", "") ?: ""
        set(v) = p.edit().putString("userName", v.trim()).apply()

    /** Heures en minutes depuis minuit. */
    var windowStart: Int
        get() = p.getInt("windowStart", 7 * 60)
        set(v) = p.edit().putInt("windowStart", v).apply()

    var reminderAt: Int
        get() = p.getInt("reminderAt", 9 * 60 + 30)
        set(v) = p.edit().putInt("reminderAt", v).apply()

    var deadline: Int
        get() = p.getInt("deadline", 10 * 60 + 15)
        set(v) = p.edit().putInt("deadline", v).apply()

    var includeLocation: Boolean
        get() = p.getBoolean("includeLocation", true)
        set(v) = p.edit().putBoolean("includeLocation", v).apply()

    /** Mode essai : aucun SMS ne part vraiment. */
    var testMode: Boolean
        get() = p.getBoolean("testMode", false)
        set(v) = p.edit().putBoolean("testMode", v).apply()

    var lastCheckIn: Long
        get() = p.getLong("lastCheckIn", 0L)
        set(v) = p.edit().putLong("lastCheckIn", v).apply()

    /** Date de reprise de la surveillance (0 = pas en veille). */
    var pausedUntil: Long
        get() = p.getLong("pausedUntil", 0L)
        set(v) = p.edit().putLong("pausedUntil", v).apply()

    /** Jour (aaaa-mm-jj) de la dernière alerte « pas de signe de vie », pour ne pas l'envoyer deux fois. */
    var alertSentDay: String
        get() = p.getString("alertSentDay", "") ?: ""
        set(v) = p.edit().putString("alertSentDay", v).apply()

    var contacts: List<Contact>
        get() {
            val out = ArrayList<Contact>()
            try {
                val arr = JSONArray(p.getString("contacts", "[]") ?: "[]")
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    out.add(Contact(o.optString("name"), o.optString("relation"), o.optString("phone")))
                }
            } catch (e: Exception) {
                // liste illisible : on repart d'une liste vide
            }
            return out
        }
        set(v) {
            val arr = JSONArray()
            for (c in v) {
                arr.put(JSONObject().put("name", c.name).put("relation", c.relation).put("phone", c.phone))
            }
            p.edit().putString("contacts", arr.toString()).apply()
        }

    fun isPaused(): Boolean = System.currentTimeMillis() < pausedUntil

    fun checkedInToday(): Boolean = lastCheckIn > 0L && dayKey(lastCheckIn) == dayKey(System.currentTimeMillis())

    companion object {
        fun dayKey(millis: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))

        /** 435 -> « 7 h 15 », 420 -> « 7 h » */
        fun hm(minutes: Int): String {
            val h = minutes / 60
            val m = minutes % 60
            return if (m == 0) "$h h" else "$h h " + String.format(Locale.US, "%02d", m)
        }

        fun hmOf(millis: Long): String {
            val c = Calendar.getInstance()
            c.timeInMillis = millis
            return hm(c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE))
        }

        /** « dimanche 4 octobre » */
        fun longDate(millis: Long): String = SimpleDateFormat("EEEE d MMMM", Locale.FRENCH).format(Date(millis))
    }
}
