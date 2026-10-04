package pf.tiai.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar

class MainActivity : Activity() {

    private enum class Screen { HOME, SOS, SENT, PAUSE, CONTACTS, SETTINGS }

    private lateinit var store: Store
    private var screen = Screen.HOME
    private var timer: CountDownTimer? = null
    private var lastSentCount = 0
    private var pauseCustom = 0L

    private val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
    private val match = ViewGroup.LayoutParams.MATCH_PARENT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        Alarms.scheduleAll(this)
        askPermissions()
    }

    override fun onResume() {
        super.onResume()
        if (screen == Screen.HOME) show(Screen.HOME)
    }

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (screen == Screen.HOME) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        } else {
            show(Screen.HOME)
        }
    }

    // ---------- Autorisations ----------

    private fun askPermissions() {
        val wanted = mutableListOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= 33) wanted.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = wanted.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (screen == Screen.HOME) show(Screen.HOME)
    }

    // ---------- Outils de mise en page ----------

    private fun show(s: Screen) {
        timer?.cancel()
        timer = null
        screen = s
        setContentView(
            when (s) {
                Screen.HOME -> home()
                Screen.SOS -> sos()
                Screen.SENT -> sent()
                Screen.PAUSE -> pause()
                Screen.CONTACTS -> contacts()
                Screen.SETTINGS -> settings()
            }
        )
    }

    private fun page(bg: Int = Ui.GROUND, build: LinearLayout.() -> Unit): View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(24), dp(24), dp(24), dp(24))
        col.build()
        val scroll = ScrollView(this)
        scroll.isFillViewport = true
        scroll.setBackgroundColor(bg)
        scroll.addView(col, ViewGroup.LayoutParams(match, wrap))
        return scroll
    }

    private fun LinearLayout.add(v: View, top: Int = 0, height: Int = wrap, weight: Float = 0f) {
        val lp = LinearLayout.LayoutParams(match, height, weight)
        lp.topMargin = dp(top)
        addView(v, lp)
    }

    private fun LinearLayout.backLink() {
        val b = Ui.button(this@MainActivity, "‹  Retour", Ui.GROUND, Ui.BLUE, 20f) { show(Screen.HOME) }
        val lp = LinearLayout.LayoutParams(wrap, dp(52))
        addView(b, lp)
    }

    private fun banner(text: String, fill: Int, color: Int): TextView {
        val t = Ui.text(this, text, 18f, color, true)
        t.background = Ui.box(this, fill, null, 14)
        t.setPadding(dp(16), dp(12), dp(16), dp(12))
        return t
    }

    /** Petit libellé au-dessus, valeur en gras en dessous. */
    private fun twoLines(label: String, value: String): CharSequence {
        val s = SpannableString(label + "\n" + value)
        s.setSpan(RelativeSizeSpan(0.78f), 0, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        s.setSpan(StyleSpan(Typeface.NORMAL), 0, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return s
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    /** Dit honnêtement si les proches ont été prévenus ou non. */
    private fun report(sentCount: Int, what: String) = toast(
        when {
            store.testMode -> "$what Mode essai : aucun SMS envoyé."
            sentCount > 0 -> "$what Vos proches sont prévenus par SMS."
            else -> "$what Attention : aucun SMS n'a pu partir."
        }
    )

    private fun capitalized(s: String): String = s.replaceFirstChar { it.uppercase() }

    // ---------- Accueil ----------

    private fun home(): View = page {
        val me = this@MainActivity

        val head = LinearLayout(me)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(TiareView(me), LinearLayout.LayoutParams(dp(60), dp(60)))
        val brand = LinearLayout.LayoutParams(0, wrap, 1f)
        brand.marginStart = dp(10)
        head.addView(Ui.text(me, "Tīaʻi", 24f, Ui.BLUE, true), brand)
        head.addView(
            Ui.button(me, "Réglages", Ui.WHITE, Ui.INK, 18f, Ui.LINE) { show(Screen.SETTINGS) },
            LinearLayout.LayoutParams(wrap, dp(56))
        )
        add(head)

        val hello = if (store.userName.isBlank()) "Ia ora na" else "Ia ora na, " + store.userName
        add(Ui.text(me, hello, 30f, Ui.INK, true), top = 16)
        add(Ui.text(me, capitalized(Store.longDate(System.currentTimeMillis())), 19f, Ui.MUTED), top = 2)

        if (store.testMode) {
            add(banner("Mode essai : aucun SMS n'est envoyé.", Ui.AMBER_SOFT, Ui.AMBER), top = 12)
        }
        if (store.contacts.isEmpty()) {
            add(
                Ui.button(me, "Commencez par ajouter un proche à prévenir", Ui.AMBER_SOFT, Ui.AMBER, 19f, Ui.AMBER) {
                    show(Screen.CONTACTS)
                }, top = 12
            )
        } else if (!SmsSender.hasPermission(me)) {
            add(
                Ui.button(me, "Autoriser l'envoi de SMS (obligatoire)", Ui.AMBER_SOFT, Ui.AMBER, 19f, Ui.AMBER) {
                    if (shouldShowRequestPermissionRationale(Manifest.permission.SEND_SMS)) {
                        askPermissions()
                    } else {
                        // refus définitif : on ouvre la fiche de l'application dans les réglages Android
                        askPermissions()
                        startActivity(
                            Intent(
                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:$packageName")
                            )
                        )
                    }
                }, top = 12
            )
        }

        // Signe de vie
        if (store.isPaused()) {
            add(
                banner(
                    "En veille. Le signe de vie sera de nouveau demandé le " +
                        Store.longDate(store.pausedUntil) + ". Le SOS reste actif.",
                    Ui.BLUE_SOFT, Ui.INK
                ), top = 16
            )
            add(
                Ui.button(me, "Reprendre la surveillance", Ui.WHITE, Ui.BLUE, 20f, Ui.BLUE) {
                    store.pausedUntil = 0L
                    report(SmsSender.sendToAll(me, Messages.resume(store)), "Surveillance reprise.")
                    show(Screen.HOME)
                }, top = 10, height = dp(68)
            )
        } else if (store.checkedInToday()) {
            add(
                Ui.text(me, "Signe de vie du jour : donné à " + Store.hmOf(store.lastCheckIn) + ".", 19f),
                top = 16
            )
            val ok = Ui.text(me, "✓  Merci, c'est noté", 26f, Ui.GREEN, true)
            ok.gravity = Gravity.CENTER
            ok.background = Ui.box(me, Ui.WHITE, Ui.GREEN, 20)
            add(ok, top = 10, height = dp(120))
        } else {
            add(
                Ui.text(
                    me,
                    "Signe de vie du jour : à donner entre " + Store.hm(store.windowStart) +
                        " et " + Store.hm(store.deadline) + ".",
                    19f
                ), top = 16
            )
            add(
                Ui.button(me, "✓  Je vais bien", Ui.BLUE, Ui.WHITE, 30f) {
                    store.lastCheckIn = System.currentTimeMillis()
                    show(Screen.HOME)
                }, top = 10, height = dp(120)
            )
        }

        // SOS
        val label = SpannableString("SOS\nAppuyer pour alerter mes proches")
        label.setSpan(RelativeSizeSpan(0.28f), 4, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val sosButton = Ui.button(me, label, Ui.RED, Ui.WHITE, 72f) {
            if (store.contacts.isEmpty()) {
                toast("Ajoutez d'abord un proche à prévenir.")
                show(Screen.CONTACTS)
            } else {
                show(Screen.SOS)
            }
        }
        sosButton.minHeight = dp(210)
        sosButton.minimumHeight = dp(210)
        add(sosButton, top = 16, height = 0, weight = 1f)

        val row = LinearLayout(me)
        row.orientation = LinearLayout.HORIZONTAL
        val left = LinearLayout.LayoutParams(0, dp(68), 1f)
        left.marginEnd = dp(6)
        val right = LinearLayout.LayoutParams(0, dp(68), 1f)
        right.marginStart = dp(6)
        row.addView(Ui.button(me, "Mettre en veille", Ui.WHITE, Ui.INK, 18f, Ui.LINE) { show(Screen.PAUSE) }, left)
        row.addView(Ui.button(me, "Mes proches", Ui.WHITE, Ui.INK, 18f, Ui.LINE) { show(Screen.CONTACTS) }, right)
        add(row, top = 16)

        add(ShellBand(me), top = 14, height = dp(52))
    }

    // ---------- SOS ----------

    private fun sos(): View {
        if (store.includeLocation) LocationHelper.warmUp(this)

        val number = Ui.text(this, "5", 130f, Ui.WHITE, true)
        number.gravity = Gravity.CENTER
        number.includeFontPadding = false
        val ring = GradientDrawable()
        ring.shape = GradientDrawable.OVAL
        ring.setStroke(dp(8), Ui.WHITE)
        number.background = ring

        timer = object : CountDownTimer(5000L, 200L) {
            override fun onTick(millisLeft: Long) {
                number.text = ((millisLeft + 999L) / 1000L).toString()
            }

            override fun onFinish() {
                sendSos()
            }
        }.start()

        val count = store.contacts.size
        return page(Ui.RED_DARK) {
            val me = this@MainActivity
            val title = Ui.text(me, "Alerte dans", 32f, Ui.WHITE, true)
            title.gravity = Gravity.CENTER
            add(title, top = 8)

            val lp = LinearLayout.LayoutParams(dp(230), dp(230))
            lp.gravity = Gravity.CENTER_HORIZONTAL
            lp.topMargin = dp(28)
            addView(number, lp)

            val info = Ui.text(
                me,
                if (count > 1) "Un SMS sera envoyé à vos $count proches." else "Un SMS sera envoyé à votre proche.",
                22f, Ui.WHITE
            )
            info.gravity = Gravity.CENTER
            add(info, top = 24)

            add(View(me), height = 0, weight = 1f)
            add(Ui.button(me, "Annuler", Ui.WHITE, 0xFF7A1107.toInt(), 34f) { show(Screen.HOME) }, top = 24, height = dp(120))
            add(
                Ui.button(me, "Envoyer tout de suite", Ui.RED_DARK, Ui.WHITE, 21f, Ui.WHITE) { sendSos() },
                top = 14, height = dp(68)
            )
        }
    }

    private fun sendSos() {
        timer?.cancel()
        timer = null
        val loc = if (store.includeLocation) LocationHelper.best(this) else null
        lastSentCount = SmsSender.sendToAll(this, Messages.sos(store, loc))
        show(Screen.SENT)
    }

    private fun sent(): View = page {
        val me = this@MainActivity
        val ok = lastSentCount > 0
        add(
            Ui.text(
                me,
                when {
                    store.testMode -> "Mode essai"
                    ok -> "Alerte envoyée"
                    else -> "Alerte non envoyée"
                },
                34f, Ui.RED_DARK, true
            ), top = 8
        )
        add(
            Ui.text(
                me,
                when {
                    store.testMode -> "Aucun SMS n'est parti. Désactivez le mode essai dans les réglages pour une vraie alerte."
                    ok -> "À " + Store.hmOf(System.currentTimeMillis()) + ", par SMS."
                    else -> "Aucun SMS n'a pu partir (autorisation SMS refusée ou pas de réseau). Appelez le 15."
                },
                20f, Ui.MUTED
            ), top = 6
        )

        if (ok) {
            for (c in store.contacts) {
                val line = Ui.text(me, twoLines(c.relation.ifBlank { "Proche" }, c.name), 21f, Ui.INK, true)
                line.background = Ui.box(me, Ui.WHITE, Ui.LINE, 16)
                line.setPadding(dp(18), dp(12), dp(18), dp(12))
                add(line, top = 10)
            }
        }

        add(View(me), height = 0, weight = 1f)
        add(
            Ui.button(me, "Appeler le 15 (SAMU)", Ui.RED, Ui.WHITE, 26f) {
                try {
                    startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:15")))
                } catch (e: Exception) {
                    toast("Composez le 15 sur votre téléphone.")
                }
            }, top = 24, height = dp(96)
        )
        if (ok) {
            add(
                Ui.button(me, "Fausse alerte : rassurer mes proches", Ui.WHITE, Ui.BLUE, 20f, Ui.BLUE) {
                    report(SmsSender.sendToAll(me, Messages.falseAlarm(store)), "Fausse alerte signalée.")
                    show(Screen.HOME)
                }, top = 14, height = dp(72)
            )
        }
        add(Ui.button(me, "Retour à l'accueil", Ui.GROUND, Ui.BLUE, 20f) { show(Screen.HOME) }, top = 8, height = dp(60))
    }

    // ---------- Mise en veille ----------

    private fun pause(): View = page {
        val me = this@MainActivity
        pauseCustom = 0L
        backLink()
        add(Ui.text(me, "Mettre en veille", 32f, Ui.INK, true), top = 8)
        add(Ui.text(me, "Le signe de vie ne sera plus demandé :", 19f, Ui.MUTED), top = 6)

        val labels = listOf("Aujourd'hui seulement", "Pendant 3 jours", "Pendant 1 semaine", "Jusqu'à une date à choisir")
        val group = RadioGroup(me)
        val radios = ArrayList<RadioButton>()
        for ((i, l) in labels.withIndex()) {
            val r = RadioButton(me)
            r.id = 100 + i
            r.text = l
            r.textSize = 21f
            r.setTextColor(Ui.INK)
            r.typeface = Typeface.DEFAULT_BOLD
            r.minHeight = dp(68)
            r.setPadding(dp(8), 0, dp(8), 0)
            r.background = Ui.box(me, Ui.WHITE, Ui.LINE, 16)
            val lp = RadioGroup.LayoutParams(match, wrap)
            lp.topMargin = dp(10)
            group.addView(r, lp)
            radios.add(r)
        }
        group.check(100)
        radios[3].setOnClickListener {
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_YEAR, 2)
            val dlg = DatePickerDialog(me, { _, y, m, d ->
                val picked = Calendar.getInstance()
                picked.clear()
                picked.set(y, m, d, 0, 0, 0)
                pauseCustom = picked.timeInMillis
                radios[3].text = "Jusqu'au " + Store.longDate(pauseCustom)
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
            dlg.datePicker.minDate = System.currentTimeMillis() + 24L * 3600L * 1000L
            dlg.show()
        }
        add(group, top = 6)

        add(
            banner(
                "Vos proches recevront un SMS pour les prévenir. Le bouton SOS reste actif pendant la veille.",
                Ui.BLUE_SOFT, Ui.INK
            ), top = 16
        )
        add(View(me), height = 0, weight = 1f)
        add(
            Ui.button(me, "Mettre en veille et prévenir mes proches", Ui.BLUE, Ui.WHITE, 21f) {
                val until = pauseUntil(group.checkedRadioButtonId - 100)
                if (until <= System.currentTimeMillis()) {
                    toast("Choisissez d'abord la date de retour.")
                } else {
                    store.pausedUntil = until
                    report(SmsSender.sendToAll(me, Messages.pause(store, until)), "Mise en veille.")
                    show(Screen.HOME)
                }
            }, top = 24, height = dp(88)
        )
    }

    /** Jour de reprise de la surveillance, à minuit. */
    private fun pauseUntil(choice: Int): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        when (choice) {
            0 -> c.add(Calendar.DAY_OF_YEAR, 1)
            1 -> c.add(Calendar.DAY_OF_YEAR, 3)
            2 -> c.add(Calendar.DAY_OF_YEAR, 7)
            else -> return pauseCustom
        }
        return c.timeInMillis
    }

    // ---------- Proches ----------

    private fun contacts(): View = page {
        val me = this@MainActivity
        backLink()
        add(Ui.text(me, "Mes proches", 32f, Ui.INK, true), top = 8)
        add(Ui.text(me, "Ils reçoivent vos alertes par SMS.", 19f, Ui.MUTED), top = 6)

        val list = store.contacts
        if (list.isEmpty()) {
            add(banner("Aucun proche enregistré pour l'instant.", Ui.AMBER_SOFT, Ui.AMBER), top = 16)
        }
        for ((i, c) in list.withIndex()) {
            val card = LinearLayout(me)
            card.orientation = LinearLayout.VERTICAL
            card.background = Ui.box(me, Ui.WHITE, Ui.LINE, 16)
            card.setPadding(dp(18), dp(14), dp(18), dp(12))
            card.addView(Ui.text(me, c.name, 23f, Ui.INK, true))
            if (c.relation.isNotBlank()) card.addView(Ui.text(me, c.relation, 18f, Ui.MUTED))
            card.addView(Ui.text(me, c.phone, 21f))
            val del = Ui.button(me, "Supprimer", Ui.WHITE, Ui.RED_DARK, 17f, Ui.LINE) {
                AlertDialog.Builder(me)
                    .setMessage("Supprimer " + c.name + " de la liste ?")
                    .setPositiveButton("Supprimer") { _, _ ->
                        store.contacts = store.contacts.filterIndexed { index, _ -> index != i }
                        show(Screen.CONTACTS)
                    }
                    .setNegativeButton("Annuler", null)
                    .show()
            }
            val lp = LinearLayout.LayoutParams(wrap, dp(52))
            lp.topMargin = dp(8)
            card.addView(del, lp)
            add(card, top = 12)
        }

        add(View(me), height = 0, weight = 1f)
        add(Ui.button(me, "+  Ajouter un proche", Ui.BLUE, Ui.WHITE, 24f) { addContactDialog() }, top = 24, height = dp(88))
    }

    private fun field(hint: String, type: Int): EditText {
        val e = EditText(this)
        e.hint = hint
        e.textSize = 21f
        e.inputType = type
        e.minHeight = dp(60)
        return e
    }

    private fun addContactDialog() {
        val name = field("Prénom", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        val relation = field("Lien (fils, fille, voisine…)", InputType.TYPE_CLASS_TEXT)
        val phone = field("Numéro de téléphone", InputType.TYPE_CLASS_PHONE)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(20), dp(8), dp(20), 0)
        col.addView(name)
        col.addView(relation)
        col.addView(phone)
        AlertDialog.Builder(this)
            .setTitle("Ajouter un proche")
            .setView(col)
            .setPositiveButton("Enregistrer") { _, _ ->
                val n = name.text.toString().trim()
                val ph = phone.text.toString().trim().replace(" ", "")
                if (n.isEmpty() || ph.count { it.isDigit() } < 6) {
                    toast("Il faut un prénom et un numéro de téléphone valide.")
                } else {
                    store.contacts = store.contacts + Contact(n, relation.text.toString().trim(), ph)
                    show(Screen.CONTACTS)
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    // ---------- Réglages ----------

    private fun settings(): View = page {
        val me = this@MainActivity
        backLink()
        add(Ui.text(me, "Réglages", 32f, Ui.INK, true), top = 8)

        fun row(label: String, value: String, onClick: () -> Unit) {
            val b = Ui.button(me, twoLines(label, value), Ui.WHITE, Ui.INK, 21f, Ui.LINE, onClick)
            b.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            b.setPadding(dp(18), dp(12), dp(18), dp(12))
            b.minHeight = dp(80)
            add(b, top = 10)
        }

        fun timeRow(label: String, current: Int, save: (Int) -> Unit) {
            row(label, Store.hm(current)) {
                TimePickerDialog(me, { _, h, m ->
                    save(h * 60 + m)
                    Alarms.scheduleAll(me)
                    show(Screen.SETTINGS)
                }, current / 60, current % 60, true).show()
            }
        }

        row("Mon prénom", store.userName.ifBlank { "À renseigner" }) {
            val e = field("Prénom", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
            e.setText(store.userName)
            val wrapBox = LinearLayout(me)
            wrapBox.setPadding(dp(20), dp(8), dp(20), 0)
            wrapBox.addView(e, LinearLayout.LayoutParams(match, wrap))
            AlertDialog.Builder(me)
                .setTitle("Mon prénom")
                .setView(wrapBox)
                .setPositiveButton("Enregistrer") { _, _ ->
                    store.userName = e.text.toString()
                    show(Screen.SETTINGS)
                }
                .setNegativeButton("Annuler", null)
                .show()
        }
        timeRow("Signe de vie à donner à partir de", store.windowStart) { store.windowStart = it }
        timeRow("Rappel sonore si j'oublie", store.reminderAt) { store.reminderAt = it }
        timeRow("Sans signe de vie, prévenir mes proches à", store.deadline) { store.deadline = it }
        row("Position dans le SMS d'alerte", if (store.includeLocation) "Activée" else "Désactivée") {
            store.includeLocation = !store.includeLocation
            show(Screen.SETTINGS)
        }
        row("Mode essai (aucun SMS envoyé)", if (store.testMode) "Activé" else "Désactivé") {
            store.testMode = !store.testMode
            show(Screen.SETTINGS)
        }

        add(
            Ui.button(me, "Envoyer un SMS d'essai à mes proches", Ui.WHITE, Ui.BLUE, 19f, Ui.BLUE) {
                when {
                    store.contacts.isEmpty() -> toast("Ajoutez d'abord un proche.")
                    store.testMode -> toast("Mode essai activé : aucun SMS ne part.")
                    else -> {
                        val n = SmsSender.sendToAll(me, Messages.test(store))
                        toast(if (n > 0) "SMS d'essai envoyé à $n proche(s)." else "Aucun SMS n'a pu partir.")
                    }
                }
            }, top = 18, height = dp(72)
        )
        add(
            Ui.text(
                me,
                "Tīaʻi prévient vos proches. Elle ne remplace pas les secours : en cas d'urgence vitale, appelez le 15.",
                17f, Ui.MUTED
            ), top = 18
        )
        add(ShellBand(me), top = 14, height = dp(52))
    }
}
