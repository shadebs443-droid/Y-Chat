package com.ycona.ychat

import android.app.Activity
import android.os.Bundle
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.*
import android.text.InputType
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val BG = Color.rgb(24, 22, 30)
private val SURFACE = Color.rgb(31, 29, 39)
private val PANEL = Color.rgb(42, 39, 51)
private val TEXT = Color.rgb(248, 246, 251)
private val MUTED = Color.rgb(157, 151, 169)
private val GRADIENT = intArrayOf(0xff2678ed.toInt(), 0xff5c65c8.toInt(), 0xff9850c6.toInt(), 0xffb020b5.toInt(), 0xff673ab7.toInt())

class MainActivity : Activity() {
    private lateinit var net: YChatSocket
    private lateinit var root: FrameLayout
    private lateinit var page: LinearLayout
    private lateinit var content: LinearLayout
    private val prefs by lazy { getSharedPreferences("ychat", MODE_PRIVATE) }
    private var serverUrl = ""
    private var token = ""
    private var currentConv = -1
    private var currentTitle = ""
    private var chats = JSONArray()
    private var history = JSONArray()
    private var searchQuery = ""
    private var drawer: PopupWindow? = null
    private var authStatus: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        window.decorView.systemUiVisibility = 0
        net = YChatSocket { event -> runOnUiThread { handle(event) } }
        serverUrl = prefs.getString("server", "") ?: ""
        token = prefs.getString("token", "") ?: ""
        if (token.isNotBlank() && serverUrl.isNotBlank()) connect(JSONObject().put("action", "resume").put("token", token)) else showAuth()
    }

    private fun shell(): LinearLayout {
        root = FrameLayout(this).apply { setBackgroundColor(BG) }
        page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BG) }
        root.addView(page, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8)) }
        page.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        return page
    }

    private fun topBar(title: String, subtitle: String? = null, back: Boolean = false, right: List<Pair<String, () -> Unit>> = emptyList()): LinearLayout {
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), 0, dp(8), 0); setBackgroundColor(SURFACE) }
        val nav = iconButton(if (back) "back" else "menu", 52) { if (back) { net.send(JSONObject().put("action", "my_chats")); showChats() } else openDrawer() }
        bar.addView(nav, LinearLayout.LayoutParams(dp(52), dp(56)))
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        labels.addView(TextView(this).apply { text = title; textSize = 19f; setTextColor(TEXT); typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); maxLines = 1 })
        if (subtitle != null) labels.addView(TextView(this).apply { text = subtitle; textSize = 12f; setTextColor(MUTED); maxLines = 1 })
        bar.addView(labels, LinearLayout.LayoutParams(0, -1, 1f))
        right.forEach { (name, action) -> bar.addView(iconButton(name, 44, action), LinearLayout.LayoutParams(dp(44), dp(52))) }
        return bar
    }

    private fun showAuth() {
        shell()
        val hero = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(24), dp(38), dp(24), dp(30)); background = gradient(26) }
        hero.addView(TextView(this).apply { text = "Y-CONА"; textSize = 38f; letterSpacing = .04f; typeface = Typeface.create("sans-serif-black", Typeface.NORMAL); setTextColor(Color.WHITE) })
        hero.addView(TextView(this).apply { text = "Мессенджер"; textSize = 14f; setTextColor(0xfff0eaff.toInt()) })
        content.addView(hero, LinearLayout.LayoutParams(-1, dp(150)))
        content.addView(space(26))
        val server = field("Адрес сервера · wss://…", serverUrl)
        val username = field("Телефон, username или email")
        val password = field("Пароль", secret = true)
        content.addView(server); content.addView(username); content.addView(password)
        authStatus = TextView(this).apply { setTextColor(MUTED); textSize = 13f; setPadding(dp(4), dp(8), dp(4), dp(8)) }; content.addView(authStatus)
        content.addView(primaryButton("Войти") {
            serverUrl = normalizeServer(server.text.toString())
            if (serverUrl.isBlank() || username.text.isBlank() || password.text.isBlank()) { authStatus?.text = "Заполните сервер, логин и пароль"; return@primaryButton }
            prefs.edit().putString("server", serverUrl).putString("username", username.text.toString().trim()).apply()
            connect(JSONObject().put("action", "login").put("username", username.text.toString().trim()).put("password", password.text.toString()))
        })
        content.addView(textButton("Создать аккаунт") {
            val nick = field("Имя для профиля")
            content.addView(nick, 1)
            content.addView(primaryButton("Зарегистрироваться") {
                serverUrl = normalizeServer(server.text.toString())
                connect(JSONObject().put("action", "register").put("username", username.text.toString().trim()).put("nick", nick.text.toString().ifBlank { username.text.toString() }).put("password", password.text.toString()).put("email", "").put("code", ""))
            })
        })
    }

    private fun connect(auth: JSONObject) { authStatus?.text = "Подключение…"; net.connect(serverUrl, auth) }

    private fun handle(msg: JSONObject) {
        when {
            msg.optString("type") == "connection_error" -> { toast(msg.optString("msg", "Нет соединения")); if (token.isBlank()) showAuth() }
            msg.optString("status") == "ok" && msg.has("token") -> {
                token = msg.optString("token")
                prefs.edit().putString("token", token).putString("server", serverUrl).putString("name", msg.optString("name")).putString("email", msg.optString("email")).apply()
                net.send(JSONObject().put("action", "my_chats")); showChats()
            }
            msg.optString("status") == "ok" && msg.optString("re") == "my_chats" -> { chats = msg.optJSONArray("chats") ?: JSONArray(); if (currentConv < 0) showChats() }
            msg.optString("status") == "ok" && msg.optString("re") == "get_conv_history" -> { history = msg.optJSONArray("messages") ?: JSONArray(); showConversation() }
            msg.optString("status") == "ok" && msg.optString("re") in listOf("open_private", "open_handle", "open_bot", "create_chat") -> {
                currentConv = msg.optInt("conv_id", -1); currentTitle = msg.optString("title", "Чат")
                net.send(JSONObject().put("action", "get_conv_history").put("conv_id", currentConv)); net.send(JSONObject().put("action", "my_chats"))
            }
            msg.optString("type") == "msg" && msg.optInt("conv_id") == currentConv -> { history.put(msg); showConversation() }
            msg.optString("status") == "error" || msg.optString("type") == "error" -> toast(msg.optString("msg", "Ошибка"))
        }
    }

    private fun showChats() {
        currentConv = -1
        shell()
        page.addView(topBar("YChat", "онлайн", false, listOf("search" to { showSearch() }, "more" to { openDrawer() })), 0)
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), 0, dp(12), 0); setBackgroundColor(SURFACE) }
        listOf("Все чаты", "Личные", "Группы", "Каналы").forEachIndexed { i, label ->
            val t = TextView(this).apply { text = label; textSize = 13f; gravity = Gravity.CENTER; setTextColor(if (i == 0) 0xffc69aff.toInt() else MUTED); typeface = if (i == 0) Typeface.DEFAULT_BOLD else Typeface.DEFAULT; setPadding(dp(10), dp(13), dp(10), dp(13)) }
            tabs.addView(t, LinearLayout.LayoutParams(0, dp(44), 1f))
        }
        page.addView(tabs, 1)
        val listScroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(4), 0, dp(80)) }
        if (chats.length() == 0) {
            list.addView(TextView(this).apply { text = "Пока нет сообщений\nНайдите пользователя или создайте группу"; gravity = Gravity.CENTER; setTextColor(MUTED); textSize = 14f; setPadding(dp(24), dp(80), dp(24), dp(30)) })
        }
        for (i in 0 until chats.length()) {
            val c = chats.optJSONObject(i) ?: continue
            val title = c.optString("title", c.optString("handle", "Чат"))
            val last = c.optString("last_text", "")
            if (searchQuery.isNotBlank() && !title.contains(searchQuery, true) && !last.contains(searchQuery, true)) continue
            val cid = c.optInt("conv_id", -1)
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(8), dp(14), dp(8)); background = selectable() }
            row.addView(avatar(title, 54), LinearLayout.LayoutParams(dp(54), dp(54)))
            val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), 0, 0, 0) }
            val titleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            titleRow.addView(TextView(this).apply { text = title; textSize = 16f; setTextColor(TEXT); typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); maxLines = 1 }, LinearLayout.LayoutParams(0, -2, 1f))
            titleRow.addView(TextView(this).apply { text = c.optString("time", ""); textSize = 11f; setTextColor(MUTED) })
            details.addView(titleRow)
            details.addView(TextView(this).apply { text = last.ifBlank { "Нет сообщений" }; textSize = 13f; setTextColor(MUTED); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(0, dp(4), 0, 0) })
            row.addView(details, LinearLayout.LayoutParams(0, -1, 1f))
            row.setOnClickListener { currentConv = cid; currentTitle = title; net.send(JSONObject().put("action", "get_conv_history").put("conv_id", cid)); net.send(JSONObject().put("type", "mark_read").put("conv_id", cid)) }
            list.addView(row, LinearLayout.LayoutParams(-1, dp(72)))
            val line = View(this).apply { setBackgroundColor(0xff393541.toInt()) }
            list.addView(line, LinearLayout.LayoutParams(-1, dp(1)).apply { leftMargin = dp(78) })
        }
        listScroll.addView(list); content.addView(listScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val fab = TextView(this).apply { text = "+"; gravity = Gravity.CENTER; textSize = 32f; setTextColor(Color.WHITE); background = gradient(100); elevation = dp(8).toFloat(); setOnClickListener { showNewChatDialog() } }
        root.addView(fab, FrameLayout.LayoutParams(dp(58), dp(58), Gravity.RIGHT or Gravity.BOTTOM).apply { rightMargin = dp(20); bottomMargin = dp(22) })
    }

    private fun showConversation() {
        shell()
        page.addView(topBar(currentTitle, "был(а) недавно", true, listOf("phone" to { toast("Звонки пока не подключены") }, "more" to { showChatActions() })), 0)
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        val messages = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(14), dp(10), dp(18)); background = conversationWallpaper() }
        for (i in 0 until history.length()) {
            val m = history.optJSONObject(i) ?: continue
            val mine = m.optString("from") == prefs.getString("email", "") || m.optString("from") == prefs.getString("username", "")
            val bubble = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(6)); background = rounded(if (mine) 0xff574080.toInt() else 0xff33303c.toInt(), 17) }
            if (!mine) bubble.addView(TextView(this).apply { text = m.optString("from_name", m.optString("from")); textSize = 12f; setTextColor(0xffc79cff.toInt()); typeface = Typeface.DEFAULT_BOLD })
            bubble.addView(TextView(this).apply { text = m.optString("text"); setTextColor(TEXT); textSize = 15f; setPadding(0, if (mine) 0 else dp(3), 0, dp(2)) })
            bubble.addView(TextView(this).apply { text = m.optString("time", ""); gravity = Gravity.RIGHT; textSize = 10f; setTextColor(0xffc4bdcd.toInt()) })
            val row = LinearLayout(this).apply { gravity = if (mine) Gravity.RIGHT else Gravity.LEFT; setPadding(dp(2), dp(3), dp(2), dp(3)); addView(bubble, LinearLayout.LayoutParams(-2, -2)) }
            messages.addView(row)
        }
        scroll.addView(messages); content.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val compose = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(6), dp(7), dp(6), dp(7)); setBackgroundColor(SURFACE) }
        compose.addView(iconButton("attach", 42) { toast("Прикрепление файлов скоро появится") }, LinearLayout.LayoutParams(dp(42), dp(48)))
        val input = EditText(this).apply { hint = "Сообщение"; setHintTextColor(MUTED); setTextColor(TEXT); textSize = 15f; setSingleLine(true); background = rounded(PANEL, 24); setPadding(dp(16), 0, dp(16), 0); imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEND }
        compose.addView(input, LinearLayout.LayoutParams(0, dp(44), 1f).apply { leftMargin = dp(3); rightMargin = dp(5) })
        compose.addView(iconButton("send", 46) { sendMessage(input) }, LinearLayout.LayoutParams(dp(46), dp(48)))
        input.setOnEditorActionListener { _, action, _ -> if (action == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) { sendMessage(input); true } else false }
        content.addView(compose, LinearLayout.LayoutParams(-1, dp(60)))
    }

    private fun sendMessage(input: EditText) {
        val text = input.text.toString().trim()
        if (text.isNotEmpty()) { net.send(JSONObject().put("type", "send_msg").put("conv_id", currentConv).put("text", text).put("client_msg_id", UUID.randomUUID().toString())); input.setText("") }
    }

    private fun openDrawer() {
        drawer?.dismiss()
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(SURFACE) }
        val head = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(28), dp(16), dp(18)); background = gradient(0) }
        head.addView(avatar(prefs.getString("name", "Y") ?: "Y", 64))
        head.addView(TextView(this).apply { text = prefs.getString("name", "Мой профиль"); textSize = 18f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(12), 0, dp(2)) })
        head.addView(TextView(this).apply { text = prefs.getString("email", "@ychat"); textSize = 13f; setTextColor(0xffeee4ff.toInt()) })
        panel.addView(head, LinearLayout.LayoutParams(-1, dp(174)))
        val menuScroll = ScrollView(this)
        val menu = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, dp(8)) }
        val entries = listOf(Triple("person", "Мой профиль", 0), Triple("contacts", "Контакты", 1), Triple("bookmark", "Избранное", 2), Triple("phone", "Звонки", 3), Triple("archive", "Архив чатов", 4), Triple("group", "Создать группу", 5), Triple("channel", "Создать канал", 6), Triple("bot", "Боты", 7), Triple("settings", "Настройки", 8))
        entries.forEach { (icon, label, id) ->
            val item = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(20), 0, dp(12), 0); background = selectable() }
            item.addView(IconView(this, icon, MUTED), LinearLayout.LayoutParams(dp(26), dp(26)))
            item.addView(TextView(this).apply { text = label; textSize = 15f; setTextColor(TEXT); setPadding(dp(22), 0, 0, 0) }, LinearLayout.LayoutParams(-1, -1))
            item.setOnClickListener { drawer?.dismiss(); when (id) { 5 -> createChat("group"); 6 -> createChat("channel"); 8 -> showSettings(); 0 -> { net.send(JSONObject().put("action", "get_profile").put("who", "me")); toast("Запрос профиля отправлен") }; 7 -> { net.send(JSONObject().put("action", "my_bots")); toast("Запрос списка ботов отправлен") }; else -> toast("Раздел пока не подключён") } }
            menu.addView(item, LinearLayout.LayoutParams(-1, dp(52)))
        }
        val divider = View(this).apply { setBackgroundColor(0xff45404d.toInt()) }; menu.addView(divider, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(8); bottomMargin = dp(8) })
        val logout = drawerItem("logout", "Выйти") { drawer?.dismiss(); net.close(); token = ""; prefs.edit().remove("token").apply(); showAuth() }
        menu.addView(logout, LinearLayout.LayoutParams(-1, dp(52)))
        menuScroll.addView(menu); panel.addView(menuScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        drawer = PopupWindow(panel, dp(312), -1, true).apply { setBackgroundDrawable(rounded(SURFACE, 0)); elevation = dp(18).toFloat(); isOutsideTouchable = true; showAtLocation(root, Gravity.LEFT or Gravity.TOP, 0, 0) }
    }

    private fun drawerItem(icon: String, label: String, action: () -> Unit): View = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(20), 0, 0, 0); addView(IconView(this@MainActivity, icon, MUTED), LinearLayout.LayoutParams(dp(26), dp(26))); addView(TextView(this@MainActivity).apply { text = label; textSize = 15f; setTextColor(TEXT); setPadding(dp(22), 0, 0, 0) }); setOnClickListener { action() }; background = selectable() }

    private fun showSearch() {
        val q = field("Поиск", searchQuery)
        android.app.AlertDialog.Builder(this).setTitle("Поиск сообщений и чатов").setView(q).setNegativeButton("Отмена", null).setPositiveButton("Найти") { _, _ -> searchQuery = q.text.toString(); showChats() }.show()
    }

    private fun showNewChatDialog() {
        val options = arrayOf("Найти пользователя", "Создать группу", "Создать канал")
        android.app.AlertDialog.Builder(this).setTitle("Новое сообщение").setItems(options) { _, which -> when (which) { 0 -> prompt("Username пользователя (@username)") { net.send(JSONObject().put("action", "open_handle").put("handle", it.removePrefix("@"))) }; 1 -> createChat("group"); 2 -> createChat("channel") } }.show()
    }

    private fun showChatActions() {
        android.app.AlertDialog.Builder(this).setItems(arrayOf("Поиск в чате", "Очистить локальный экран", "Информация о чате")) { _, which -> if (which == 0) toast("Поиск в чате скоро появится") else if (which == 2) toast(currentTitle) }.show()
    }

    private fun createChat(type: String) {
        val title = field("Название")
        val desc = field("Описание (необязательно)")
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(8), dp(18), 0); addView(title); addView(desc) }
        android.app.AlertDialog.Builder(this).setTitle(if (type == "group") "Новая группа" else "Новый канал").setView(box)
            .setNegativeButton("Отмена", null).setPositiveButton("Создать") { _, _ -> net.send(JSONObject().put("action", "create_chat").put("title", title.text.toString()).put("type", type).put("members", JSONArray()).put("description", desc.text.toString())); toast("Запрос на создание отправлен") }.show()
    }

    private fun showSettings() {
        shell(); page.addView(topBar("Настройки", "YChat", true), 0)
        val rows = listOf("Уведомления и звуки", "Конфиденциальность", "Данные и память", "Оформление", "Папки чатов", "Устройства", "Язык", "О приложении")
        rows.forEach { label -> content.addView(drawerItem("settings", label) { toast("Раздел «$label» будет подключён к серверу") }, LinearLayout.LayoutParams(-1, dp(54))) }
        content.addView(space(12)); content.addView(TextView(this).apply { text = "Сервер: $serverUrl\nТема: Y-CONА Gradient"; setTextColor(MUTED); textSize = 12f })
        content.addView(textButton("Изменить адрес сервера") { prompt("WebSocket URL", serverUrl) { serverUrl = normalizeServer(it); prefs.edit().putString("server", serverUrl).apply() } })
    }

    private fun avatar(label: String, size: Int): View = FrameLayout(this).apply {
        val bg = View(this@MainActivity).apply { background = gradient(100) }; addView(bg, FrameLayout.LayoutParams(-1, -1))
        addView(TextView(this@MainActivity).apply { text = label.trim().firstOrNull()?.uppercase() ?: "Y"; gravity = Gravity.CENTER; textSize = if (size > 50) 22f else 18f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE) }, FrameLayout.LayoutParams(-1, -1))
        background = rounded(Color.TRANSPARENT, 100)
    }.also { it.clipToOutline = true }

    private fun iconButton(name: String, width: Int, action: () -> Unit): View = FrameLayout(this).apply {
        addView(IconView(this@MainActivity, name, TEXT), FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
        setOnClickListener { action() }; background = selectable(); contentDescription = name
    }
    private fun primaryButton(label: String, action: () -> Unit) = TextView(this).apply { text = label; gravity = Gravity.CENTER; textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); background = gradient(14); setPadding(dp(16), dp(13), dp(16), dp(13)); setOnClickListener { action() }; layoutParams = LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(10) } }
    private fun textButton(label: String, action: () -> Unit) = TextView(this).apply { text = label; gravity = Gravity.CENTER; textSize = 14f; setTextColor(0xffc69aff.toInt()); setPadding(dp(12), dp(12), dp(12), dp(12)); setOnClickListener { action() } }
    private fun field(hint: String, value: String = "", secret: Boolean = false): EditText = EditText(this).apply { this.hint = hint; setText(value); setTextColor(TEXT); setHintTextColor(MUTED); textSize = 15f; setSingleLine(!hint.contains("Описание")); inputType = if (secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT; background = rounded(PANEL, 14); setPadding(dp(15), dp(8), dp(15), dp(8)); layoutParams = LinearLayout.LayoutParams(-1, if (hint.contains("Описание")) dp(84) else dp(50)).apply { bottomMargin = dp(10) } }
    private fun gradient(radius: Int) = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, GRADIENT).apply { cornerRadius = dp(radius).toFloat() }
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun selectable() = android.util.TypedValue().let { val out = android.graphics.drawable.StateListDrawable(); out.addState(intArrayOf(android.R.attr.state_pressed), rounded(0xff494252.toInt(), 0)); out.addState(intArrayOf(), android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)); out }
    private fun conversationWallpaper(): android.graphics.drawable.Drawable = android.graphics.drawable.ColorDrawable(0xff211e29.toInt())
    private fun space(h: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun normalizeServer(raw: String): String { var s = raw.trim().trimEnd('/'); if (s.isBlank()) return ""; if (!s.startsWith("ws://") && !s.startsWith("wss://")) s = "wss://$s"; return s }
    private fun prompt(label: String, initial: String = "", done: (String) -> Unit) { val edit = field(label, initial); android.app.AlertDialog.Builder(this).setTitle(label).setView(edit).setNegativeButton("Отмена", null).setPositiveButton("OK") { _, _ -> done(edit.text.toString().trim()) }.show() }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    override fun onBackPressed() { if (currentConv >= 0) { net.send(JSONObject().put("action", "my_chats")); showChats() } else super.onBackPressed() }
    override fun onDestroy() { net.close(); super.onDestroy() }
}

private class IconView(context: Context, private val kind: String, private val color: Int) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = this@IconView.color; style = Paint.Style.STROKE; strokeWidth = 2.0f * resources.displayMetrics.density; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val d get() = resources.displayMetrics.density
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas); val s = minOf(width, height).toFloat(); val ox = (width - s) / 2; val oy = (height - s) / 2; canvas.save(); canvas.translate(ox, oy); canvas.scale(s / 24f, s / 24f)
        fun line(x1: Float,y1: Float,x2: Float,y2: Float) { canvas.drawLine(x1,y1,x2,y2,p) }
        fun circle(x: Float,y: Float,r: Float) { canvas.drawCircle(x,y,r,p) }
        when(kind) {
            "menu" -> { line(4f,6f,20f,6f); line(4f,12f,20f,12f); line(4f,18f,20f,18f) }
            "back" -> { line(19f,12f,5f,12f); line(5f,12f,12f,5f); line(5f,12f,12f,19f) }
            "search" -> { circle(10.5f,10.5f,6.5f); line(15.5f,15.5f,21f,21f) }
            "more" -> { circle(5f,12f,1f); circle(12f,12f,1f); circle(19f,12f,1f) }
            "send" -> { line(3f,11f,21f,3f); line(21f,3f,15f,21f); line(15f,21f,11f,13f); line(11f,13f,3f,11f); line(11f,13f,21f,3f) }
            "attach" -> { val path=Path().apply { moveTo(9f,12f); lineTo(15.5f,5.5f); cubicTo(19f,2f,23f,7f,19.5f,10.5f); lineTo(10f,20f); cubicTo(5f,25f,0f,18f,5f,13f); lineTo(14f,4f) }; canvas.drawPath(path,p) }
            "phone" -> { val path=Path().apply { moveTo(6f,3f); lineTo(9f,8f); lineTo(7f,10f); cubicTo(9f,14f,11f,16f,15f,18f); lineTo(17f,16f); lineTo(22f,19f); lineTo(20f,22f); cubicTo(11f,23f,2f,14f,2f,5f); close() }; canvas.drawPath(path,p) }
            "person" -> { circle(12f,8f,4f); canvas.drawRoundRect(4f,14f,20f,22f,5f,5f,p) }
            "contacts" -> { circle(9f,8f,3f); circle(17f,9f,2.5f); canvas.drawArc(2f,13f,16f,23f,180f,180f,false,p); canvas.drawArc(13f,14f,23f,22f,190f,160f,false,p) }
            "bookmark" -> { val path=Path().apply { moveTo(6f,3f); lineTo(18f,3f); lineTo(18f,21f); lineTo(12f,17f); lineTo(6f,21f); close() }; canvas.drawPath(path,p) }
            "archive" -> { canvas.drawRoundRect(3f,7f,21f,21f,2f,2f,p); canvas.drawRect(2f,3f,22f,7f,p); line(9f,12f,15f,12f) }
            "group" -> { circle(9f,8f,3.5f); circle(17f,9f,3f); canvas.drawArc(1f,13f,17f,23f,180f,180f,false,p); canvas.drawArc(12f,14f,23f,22f,180f,180f,false,p) }
            "channel" -> { line(4f,20f,20f,4f); line(3f,9f,15f,21f); line(9f,3f,21f,15f); circle(12f,12f,2f) }
            "bot" -> { canvas.drawRoundRect(3f,6f,21f,20f,4f,4f,p); line(12f,2f,12f,6f); circle(12f,2f,1f); circle(8f,12f,1f); circle(16f,12f,1f); line(8f,17f,16f,17f) }
            "settings" -> { circle(12f,12f,7f); circle(12f,12f,2.5f); for(a in 0..7){ val rad=Math.PI*a/4; line((12+8.5*kotlin.math.cos(rad)).toFloat(),(12+8.5*kotlin.math.sin(rad)).toFloat(),(12+10.5*kotlin.math.cos(rad)).toFloat(),(12+10.5*kotlin.math.sin(rad)).toFloat()) } }
            "logout" -> { line(10f,4f,4f,4f); line(4f,4f,4f,20f); line(4f,20f,10f,20f); line(13f,8f,18f,12f); line(18f,12f,13f,16f); line(8f,12f,18f,12f) }
        }
        canvas.restore()
    }
}
