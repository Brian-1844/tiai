package pf.tiai.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.telephony.SmsManager
import android.util.Log
import java.util.Locale
import java.util.function.Consumer

/** Textes des SMS. Écrits sans caractères spéciaux pour rester courts. */
object Messages {
    private fun who(s: Store): String = if (s.userName.isBlank()) "Votre proche" else s.userName

    fun sos(s: Store, loc: Location?): String {
        val where = if (loc != null) " Position : " + LocationHelper.link(loc) + " ." else ""
        return "URGENCE Tiai : " + who(s) + " a appuyé sur le bouton SOS à " +
            Store.hmOf(System.currentTimeMillis()) + "." + where +
            " Appelez vite, et le 15 sans réponse."
    }

    fun noSign(s: Store): String =
        "Tiai : " + who(s) + " n'a pas donné son signe de vie aujourd'hui (attendu avant " +
            Store.hm(s.deadline) + "). Merci de prendre de ses nouvelles."

    fun pause(s: Store, until: Long): String =
        "Tiai : " + who(s) + " a mis la surveillance en veille. Reprise le " +
            Store.longDate(until) + ". Le bouton SOS reste actif."

    fun resume(s: Store): String = "Tiai : " + who(s) + " a repris la surveillance."

    fun falseAlarm(s: Store): String = "Tiai : fausse alerte de " + who(s) + ". Tout va bien."

    fun test(s: Store): String =
        "Tiai : ceci est un essai de " + who(s) + ". Vous faites partie des proches à prévenir en cas d'urgence."
}

object SmsSender {
    fun hasPermission(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    /** Envoie le texte à tous les proches. Renvoie le nombre de SMS remis au téléphone pour envoi. */
    fun sendToAll(ctx: Context, text: String): Int {
        val store = Store(ctx)
        val contacts = store.contacts
        if (store.testMode) {
            Log.i("Tiai", "Mode essai, SMS non envoyé : $text")
            return 0
        }
        if (!hasPermission(ctx)) return 0
        @Suppress("DEPRECATION")
        val sms: SmsManager = if (Build.VERSION.SDK_INT >= 31) {
            ctx.getSystemService(SmsManager::class.java) ?: return 0
        } else {
            SmsManager.getDefault()
        }
        var sent = 0
        for (c in contacts) {
            try {
                val parts = sms.divideMessage(text)
                sms.sendMultipartTextMessage(c.phone, null, parts, null, null)
                sent++
            } catch (e: Exception) {
                Log.e("Tiai", "SMS non envoyé à ${c.name}", e)
            }
        }
        return sent
    }
}

object LocationHelper {
    @Volatile
    private var fresh: Location? = null

    fun hasPermission(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun keep(loc: Location?) {
        if (loc == null) return
        val cur = fresh
        if (cur == null || loc.time >= cur.time) fresh = loc
    }

    /** Demande une position récente. Appelé au début du compte à rebours du SOS. */
    @SuppressLint("MissingPermission")
    fun warmUp(ctx: Context) {
        if (!hasPermission(ctx)) return
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return
        for (prov in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            try {
                if (!lm.isProviderEnabled(prov)) continue
                if (Build.VERSION.SDK_INT >= 30) {
                    lm.getCurrentLocation(prov, null, ctx.mainExecutor, Consumer<Location?> { keep(it) })
                } else {
                    @Suppress("DEPRECATION")
                    lm.requestSingleUpdate(prov, object : LocationListener {
                        override fun onLocationChanged(location: Location) { keep(location) }
                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                        override fun onProviderEnabled(provider: String) {}
                        override fun onProviderDisabled(provider: String) {}
                    }, Looper.getMainLooper())
                }
            } catch (e: Exception) {
                Log.w("Tiai", "Position indisponible ($prov)", e)
            }
        }
    }

    /** Meilleure position connue : la plus récente entre la position fraîche et la dernière connue. */
    @SuppressLint("MissingPermission")
    fun best(ctx: Context): Location? {
        if (!hasPermission(ctx)) return null
        var best: Location? = fresh
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return best
        for (prov in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            try {
                val l = lm.getLastKnownLocation(prov) ?: continue
                val b = best
                if (b == null || l.time > b.time) best = l
            } catch (e: Exception) {
                // fournisseur absent sur ce téléphone
            }
        }
        return best
    }

    fun link(loc: Location): String =
        String.format(Locale.US, "https://maps.google.com/?q=%.5f,%.5f", loc.latitude, loc.longitude)
}
