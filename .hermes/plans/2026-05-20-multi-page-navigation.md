# AiControlAll — Multi-Page Navigation Implementation Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** Refactor the single-activity app into a multi-page navigation system with a sci-fi default page (animated planet + orbiting notes + floating device mini-planets) and 7 secondary pages accessible via a right-side drawer menu.

**Architecture:** Single MainActivity with DrawerLayout (right-side menu drawer). Content area switches between Fragment instances: DefaultPageFragment (canvas-based animation), ChatFragment, HistoryFragment, MemoryFragment, ToolsFragment, SkillsFragment, DevicesFragment, SettingsFragment. Each fragment has a RecyclerView with expandable items + search. SettingsFragment uses immediate-toggle switches.

**Tech Stack:** Kotlin, Android SDK 34, AndroidX DrawerLayout, Fragment, RecyclerView, Custom View (Canvas), ViewBinding, Coroutines

**Spec reference:** `docs/superpowers/specs/2026-05-20-multi-page-navigation-design.md`
**Prototype reference:** `.superpowers/brainstorm/sess-1779269307/content/default-page.html` (CSS animations → Canvas equivalents)

---

## Phase 1: Navigation Shell

### Task 1.1: Add DrawerLayout to activity_main.xml

**Objective:** Replace the current FrameLayout-based slide panel with AndroidX DrawerLayout

**Files:**
- Modify: `app/src/main/res/layout/activity_main.xml`

**Step 1: Rewrite layout with DrawerLayout**

Replace the entire content of `activity_main.xml` with a DrawerLayout structure:

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.drawerlayout.widget.DrawerLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/drawerLayout"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/bg_primary">

    <!-- Main content container (holds fragments) -->
    <FrameLayout
        android:id="@+id/contentFrame"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <!-- Right-side menu drawer -->
    <LinearLayout
        android:id="@+id/menuDrawer"
        android:layout_width="280dp"
        android:layout_height="match_parent"
        android:layout_gravity="end"
        android:orientation="vertical"
        android:background="@color/bg_primary">

        <TextView
            android:layout_width="match_parent"
            android:layout_height="56dp"
            android:gravity="center_vertical"
            android:paddingHorizontal="16dp"
            android:text="☰ 导航"
            android:textSize="16sp"
            android:textColor="@color/accent_cyan"
            android:textStyle="bold"
            android:fontFamily="monospace" />

        <View
            android:layout_width="match_parent"
            android:layout_height="1dp"
            android:background="#1a2332" />

        <!-- Menu items dynamically populated in code -->
        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/rvMenu"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:paddingVertical="8dp" />
    </LinearLayout>

</androidx.drawerlayout.widget.DrawerLayout>
```

**Step 2: Remove old panel and topbar views**

The old `mainContent` LinearLayout with header + chat + input, and the old `statusPanel` LinearLayout are no longer needed. They will be replaced by fragments.

**Step 3: Verify layout builds**

```bash
cd /home/lqh/projects/ai-control-all && ./gradlew assembleDebug 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/res/layout/activity_main.xml
git commit -m "refactor: replace FrameLayout with DrawerLayout shell"
```

---

### Task 1.2: Create menu item model and adapter

**Objective:** Data class + RecyclerView adapter for the drawer menu

**Files:**
- Create: `app/src/main/java/com/example/aicontrolall/ui/MenuItem.kt`
- Create: `app/src/main/java/com/example/aicontrolall/ui/MenuAdapter.kt`
- Create: `app/src/main/res/layout/item_menu.xml`

**Step 1: Create MenuItem data class**

```kotlin
// app/src/main/java/com/example/aicontrolall/ui/MenuItem.kt
package com.example.aicontrolall.ui

data class MenuItem(
    val id: String,       // "chat", "history", "memory", "tools", "skills", "devices", "settings"
    val icon: String,     // emoji icon
    val label: String,    // display name
    val badge: Int = 0,   // count badge, 0 = hidden
    val isDivider: Boolean = false
)
```

**Step 2: Create menu item layout**

```xml
<!-- app/src/main/res/layout/item_menu.xml -->
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="56dp"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:paddingHorizontal="16dp"
    android:background="?attr/selectableItemBackground">

    <TextView
        android:id="@+id/tvIcon"
        android:layout_width="36dp"
        android:layout_height="36dp"
        android:gravity="center"
        android:background="@drawable/menu_btn_bg"
        android:textSize="18sp" />

    <TextView
        android:id="@+id/tvLabel"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:layout_marginStart="12dp"
        android:textSize="14sp"
        android:textColor="@color/text_primary"
        android:fontFamily="monospace" />

    <TextView
        android:id="@+id/tvBadge"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:minWidth="24dp"
        android:gravity="center"
        android:paddingHorizontal="6dp"
        android:paddingVertical="2dp"
        android:textSize="10sp"
        android:textColor="@color/accent_green"
        android:background="@drawable/menu_btn_bg"
        android:fontFamily="monospace"
        android:visibility="gone" />
</LinearLayout>
```

**Step 3: Create MenuAdapter**

```kotlin
// app/src/main/java/com/example/aicontrolall/ui/MenuAdapter.kt
package com.example.aicontrolall.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class MenuAdapter(
    private val items: List<MenuItem>,
    private val onItemClick: (MenuItem) -> Unit
) : RecyclerView.Adapter<MenuAdapter.ViewHolder>() {

    inner class ViewHolder(val itemView: android.view.View) : RecyclerView.ViewHolder(itemView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_menu, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val iconView = holder.itemView.findViewById<android.widget.TextView>(R.id.tvIcon)
        val labelView = holder.itemView.findViewById<android.widget.TextView>(R.id.tvLabel)
        val badgeView = holder.itemView.findViewById<android.widget.TextView>(R.id.tvBadge)

        iconView.text = item.icon
        labelView.text = item.label
        if (item.badge > 0) {
            badgeView.text = item.badge.toString()
            badgeView.visibility = android.view.View.VISIBLE
        } else {
            badgeView.visibility = android.view.View.GONE
        }
        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount(): Int = items.size

    fun updateBadge(position: Int, count: Int) {
        if (position in items.indices) {
            val badgeView = (findViewHolderForAdapterPosition(position) as? ViewHolder)
                ?.itemView?.findViewById<android.widget.TextView>(R.id.tvBadge)
            if (count > 0) {
                badgeView?.text = count.toString()
                badgeView?.visibility = android.view.View.VISIBLE
            } else {
                badgeView?.visibility = android.view.View.GONE
            }
        }
    }
}
```

**Step 4: Commit**

```bash
git add app/src/main/java/com/example/aicontrolall/ui/MenuItem.kt \
        app/src/main/java/com/example/aicontrolall/ui/MenuAdapter.kt \
        app/src/main/res/layout/item_menu.xml
git commit -m "feat: add menu item model and RecyclerView adapter"
```

---

### Task 1.3: Create base TopBar layout (shared across fragments)

**Objective:** Extract the persistent top bar from MainActivity XML into a reusable layout

**Files:**
- Create: `app/src/main/res/layout/topbar.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/topbar"
    android:layout_width="match_parent"
    android:layout_height="56dp"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:background="@color/bg_primary"
    android:paddingHorizontal="16dp">

    <TextView
        android:id="@+id/tvAppTitle"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:text="AiControlAll"
        android:textSize="18sp"
        android:textColor="@color/accent_cyan"
        android:textStyle="bold"
        android:letterSpacing="0.05"
        android:fontFamily="monospace" />

    <TextView
        android:id="@+id/tvStatusPill"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:paddingHorizontal="8dp"
        android:paddingVertical="3dp"
        android:text="● M:0 S:0 T:0 D:0"
        android:textSize="10sp"
        android:textColor="@color/accent_green"
        android:background="@drawable/menu_btn_bg"
        android:fontFamily="monospace"
        android:layout_marginEnd="10dp" />

    <TextView
        android:id="@+id/btnHamburger"
        android:layout_width="36dp"
        android:layout_height="36dp"
        android:text="☰"
        android:textSize="20sp"
        android:textColor="@color/text_secondary"
        android:gravity="center"
        android:background="@drawable/menu_btn_bg"
        android:clickable="true"
        android:focusable="true" />
</LinearLayout>
```

**Step 1: Include in activity_main.xml above contentFrame**

Add `<include layout="@layout/topbar"/>` above the contentFrame in activity_main.xml.

**Step 2: Commit**

```bash
git add app/src/main/res/layout/topbar.xml app/src/main/res/layout/activity_main.xml
git commit -m "feat: add shared topbar layout"
```

---

## Phase 2: Default Page

### Task 2.1: Create DefaultPageView (custom Canvas view)

**Objective:** Custom View that renders the sci-fi default page: planet, ring, orbiting notes, floating mini-planets

**Files:**
- Create: `app/src/main/java/com/example/aicontrolall/ui/DefaultPageView.kt`

**Step 1: Write the custom view**

```kotlin
// app/src/main/java/com/example/aicontrolall/ui/DefaultPageView.kt
package com.example.aicontrolall.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.*

class DefaultPageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Planet
    private val planetPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val planetGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var planetRadius = 0f
    private var planetCx = 0f
    private var planetCy = 0f
    private var breatheScale = 1f

    // Ring
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#1a3344")
    }

    // Notes
    private data class Note(val emoji: String, val color: Int, val angle: Float, val bobOffset: Float)
    private val notes = listOf(
        Note("\u266A", Color.parseColor("#00D4FF"), 0f, 0f),
        Note("\u266B", Color.parseColor("#06D6A0"), PI.toFloat()/4, 0.3f),
        Note("\u2669", Color.parseColor("#8899aa"), PI.toFloat()/2, 0.6f),
        Note("\u266C", Color.parseColor("#00D4FF"), 3*PI.toFloat()/4, 0.9f),
        Note("\u266A", Color.parseColor("#06D6A0"), PI.toFloat(), 1.2f),
        Note("\u266B", Color.parseColor("#8899aa"), 5*PI.toFloat()/4, 0.2f),
        Note("\u2669", Color.parseColor("#00D4FF"), 3*PI.toFloat()/2, 0.5f),
        Note("\u266C", Color.parseColor("#06D6A0"), 7*PI.toFloat()/4, 1.5f)
    )
    private val noteTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 36f
        textAlign = Paint.Align.CENTER
    }
    private var orbitAngle = 0f

    // Mini planets
    private data class MiniPlanet(
        val icon: String, val label: String, val color: Int,
        val baseX: Float, val baseY: Float, val isOnline: Boolean
    )
    private val miniPlanets = mutableListOf<MiniPlanet>()
    private val miniPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Stars
    private data class Star(val x: Float, val y: Float, val r: Float, val alpha: Float, val phase: Float)
    private val stars = mutableListOf<Star>()
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    // Animators
    private val orbitAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 16000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { orbitAngle = it.animatedValue as Float; invalidate() }
    }
    private val breatheAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2000
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        addUpdateListener {
            breatheScale = 1f + 0.06f * (it.animatedValue as Float); invalidate()
        }
    }
    private val bobAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2800
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener { invalidate() }
    }

    init {
        // Generate random stars
        val rng = java.util.Random(42)
        repeat(15) {
            stars.add(Star(rng.nextFloat(), rng.nextFloat(), rng.nextFloat() * 1.5f + 0.5f,
                rng.nextFloat() * 0.4f + 0.3f, rng.nextFloat() * PI.toFloat() * 2))
        }
        // Default mini planets
        val cx = 0.5f; val cy = 0.45f
        val positions = listOf(
            Triple(cx + 0.14f, cy - 0.22f, true), Triple(cx + 0.27f, cy - 0.08f, true),
            Triple(cx + 0.20f, cy + 0.20f, true), Triple(cx - 0.22f, cy + 0.22f, true),
            Triple(cx - 0.15f, cy - 0.18f, true), Triple(cx + 0.05f, cy - 0.28f, true),
            Triple(cx - 0.26f, cy + 0.02f, false), Triple(cx + 0.10f, cy + 0.08f, false)
        )
        val icons = listOf("\uD83D\uDCF7", "\uD83C\uDFA4", "\uD83D\uDD0A", "\uD83D\uDDA5",
            "\uD83C\uDF0E", "\u2699", "\uD83C\uDFA7", "\u231A")
        val labels = listOf("摄像", "麦克", "扬声", "屏幕", "定位", "传感", "耳机", "手表")
        val colors = listOf(
            Color.parseColor("#ff5e5e"), Color.parseColor("#00D4FF"),
            Color.parseColor("#ffaa00"), Color.parseColor("#00D4FF"),
            Color.parseColor("#06D6A0"), Color.parseColor("#8899aa"),
            Color.parseColor("#f0a030"), Color.parseColor("#f0a030")
        )
        for (i in positions.indices) {
            miniPlanets.add(MiniPlanet(icons[i], labels[i], colors[i],
                positions[i].first, positions[i].second, positions[i].third))
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        planetRadius = min(w, h) * 0.16f
        planetCx = w * 0.5f
        planetCy = h * 0.45f
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        orbitAnimator.start()
        breatheAnimator.start()
        bobAnimator.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        orbitAnimator.cancel()
        breatheAnimator.cancel()
        bobAnimator.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()

        // Background
        canvas.drawColor(Color.parseColor("#0A0E17"))

        // Stars
        for (star in stars) {
            val alpha = (star.alpha * (0.6f + 0.4f * sin(System.currentTimeMillis() / 1000f * PI.toFloat() * 2 / 3f + star.phase))).coerceIn(0f, 1f)
            starPaint.alpha = (alpha * 255).toInt()
            canvas.drawCircle(star.x * w, star.y * h, star.r * 3f, starPaint)
        }

        // Glow ring
        val glowAlpha = (40 + 30 * sin(System.currentTimeMillis() / 2000f * PI.toFloat() * 2)).toInt().coerceIn(0, 255)
        planetGlowPaint.shader = RadialGradient(planetCx, planetCy, planetRadius * 1.15f,
            Color.argb(glowAlpha, 0, 212, 255), Color.argb(0, 0, 0, 0), Shader.TileMode.CLAMP)
        canvas.drawCircle(planetCx, planetCy, planetRadius * 1.15f, planetGlowPaint)

        // Planet body
        val br = planetRadius * breatheScale
        planetPaint.shader = RadialGradient(planetCx - br * 0.25f, planetCy - br * 0.3f, br,
            intArrayOf(Color.parseColor("#0a1a2a"), Color.parseColor("#162840"),
                Color.parseColor("#102030"), Color.parseColor("#060e18")),
            floatArrayOf(0f, 0.4f, 0.7f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(planetCx, planetCy, br, planetPaint)

        // Decorative ring (static)
        canvas.drawOval(planetCx - planetRadius * 1.45f, planetCy - planetRadius * 0.25f,
            planetCx + planetRadius * 1.45f, planetCy + planetRadius * 0.25f, ringPaint)

        // Orbiting notes
        val orbitRadiusX = planetRadius * 1.45f
        val orbitRadiusY = planetRadius * 0.25f
        val orbitRad = Math.toRadians(orbitAngle.toDouble())
        for ((i, note) in notes.withIndex()) {
            val angle = note.angle + orbitRad.toFloat()
            val nx = planetCx + orbitRadiusX * cos(angle)
            val ny = planetCy + orbitRadiusY * sin(angle)
            val bob = sin((System.currentTimeMillis() / 1000f * PI.toFloat() * 2 / (2.5f + i * 0.3f)) + note.bobOffset * PI.toFloat()) * 12f
            noteTextPaint.color = note.color
            noteTextPaint.alpha = (180 + 75 * abs(sin(System.currentTimeMillis() / 1000f))).toInt()
            canvas.drawText(note.emoji, nx, ny + bob, noteTextPaint)
        }

        // Mini planets
        for ((i, mp) in miniPlanets.withIndex()) {
            val mx = mp.baseX * w
            val my = mp.baseY * h
            val drift = sin(System.currentTimeMillis() / 1000f * PI.toFloat() * 2 / (6f + i) + i * 1.5f)
            val dx = drift * 6f; val dy = cos(drift * 2) * 5f
            val mr = planetRadius * 0.22f
            miniPaint.shader = RadialGradient(mx + dx, my + dy, mr,
                intArrayOf(Color.parseColor("#1a2535"), Color.parseColor("#0a1520")),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
            canvas.drawCircle(mx + dx, my + dy, mr, miniPaint)
            // Status dot
            miniPaint.shader = null
            miniPaint.color = if (mp.isOnline) Color.parseColor("#06D6A0") else Color.parseColor("#f0a030")
            miniPaint.alpha = if (mp.isOnline) 255 else 150
            canvas.drawCircle(mx + dx + mr * 0.3f, my + dy - mr * 0.5f, mr * 0.1f, miniPaint)
            // Icon
            noteTextPaint.color = Color.WHITE
            noteTextPaint.alpha = if (mp.isOnline) 255 else 150
            noteTextPaint.textSize = mr * 0.8f
            canvas.drawText(mp.icon, mx + dx, my + dy + mr * 0.15f, noteTextPaint)
            // Label
            noteTextPaint.textSize = mr * 0.35f
            noteTextPaint.color = Color.parseColor("#5a6a80")
            noteTextPaint.alpha = if (mp.isOnline) 255 else 150
            canvas.drawText(mp.label, mx + dx, my + dy + mr * 0.6f, noteTextPaint)
        }

        // Voice wave bars
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00D4FF"); alpha = 100 }
        val barHeights = floatArrayOf(8f, 16f, 24f, 16f, 8f)
        val barX = w / 2 - 20f
        val barY = h * 0.78f
        for (i in barHeights.indices) {
            val scale = 0.6f + 0.4f * sin(System.currentTimeMillis() / 300f * PI.toFloat() + i * 0.6f)
            canvas.drawRoundRect(barX + i * 8f, barY - barHeights[i] * scale / 2,
                barX + i * 8f + 4f, barY + barHeights[i] * scale / 2, 2f, 2f, barPaint)
        }

        // Status text
        noteTextPaint.textSize = 30f
        noteTextPaint.color = Color.parseColor("#5a6a80")
        noteTextPaint.alpha = (150 + 80 * sin(System.currentTimeMillis() / 1500f)).toInt()
        canvas.drawText("正在聆听…", w / 2f, h * 0.85f, noteTextPaint)
    }
}
```

**Step 2: Verify it compiles**

```bash
cd /home/lqh/projects/ai-control-all && ./gradlew assembleDebug 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/example/aicontrolall/ui/DefaultPageView.kt
git commit -m "feat: add default page custom view with planet animation"
```

---

### Task 2.2: Create DefaultPageFragment

**Objective:** Fragment wrapper for DefaultPageView

**Files:**
- Create: `app/src/main/java/com/example/aicontrolall/ui/DefaultPageFragment.kt`

```kotlin
package com.example.aicontrolall.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

class DefaultPageFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = DefaultPageView(requireContext())
}
```

**Step 1: Commit**

```bash
git add app/src/main/java/com/example/aicontrolall/ui/DefaultPageFragment.kt
git commit -m "feat: add DefaultPageFragment wrapper"
```

---

## Phase 3: Secondary Page Fragments

### Task 3.1: Create ChatFragment

**Objective:** Extract chat UI from MainActivity into a Fragment

**Files:**
- Create: `app/src/main/res/layout/fragment_chat.xml`
- Create: `app/src/main/java/com/example/aicontrolall/ui/ChatFragment.kt`
- Modify: `app/src/main/java/com/example/aicontrolall/ui/MainActivity.kt`

**Step 1: Create fragment_chat.xml**

Move the chat RecyclerView + input bar from old activity_main.xml:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="@color/bg_primary">

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/rvChat"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:clipToPadding="false"
        android:padding="10dp" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:background="@color/bg_primary"
        android:padding="10dp"
        android:gravity="center_vertical">

        <EditText
            android:id="@+id/etInput"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:hint="消息..."
            android:textColorHint="@color/text_muted"
            android:maxLines="4"
            android:background="@drawable/edittext_bg"
            android:padding="12dp"
            android:textSize="15sp"
            android:textColor="@color/text_primary" />

        <ImageButton
            android:id="@+id/btnSend"
            android:layout_width="44dp"
            android:layout_height="44dp"
            android:layout_marginStart="8dp"
            android:src="@android:drawable/ic_menu_send"
            android:background="@drawable/btn_send_bg"
            android:scaleType="centerInside" />
    </LinearLayout>
</LinearLayout>
```

**Step 2: Create ChatFragment**

Move the chat logic (sendMessage, processUserInput, chatAdapter, etc.) from MainActivity to ChatFragment. ChatFragment holds its own references to agentCore, memoryStore, etc. — these come from a shared ViewModel or are passed via the Activity.

```kotlin
class ChatFragment : Fragment() {
    // Move chat-related logic from MainActivity here
    // Input handling, RecyclerView, ChatAdapter, agentCore.processInput()
}
```

Full code in the fragment matches the existing MainActivity logic minus the panel/settings code.

**Step 3: Commit**

```bash
git add app/src/main/res/layout/fragment_chat.xml app/src/main/java/com/example/aicontrolall/ui/ChatFragment.kt
git commit -m "feat: extract chat into ChatFragment"
```

---

### Task 3.2: Create base secondary page fragment pattern

**Objective:** Create a reusable pattern for all secondary pages (history/memory/tools/skills/devices/settings)

**Files:**
- Create: `app/src/main/res/layout/fragment_secondary.xml` (shared layout: ← back + title + count + search + RecyclerView)
- Create: `app/src/main/res/layout/item_expandable.xml` (expandable list item layout)

**Step 1: Create fragment_secondary.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="@color/bg_primary">

    <!-- Header: back arrow + title + count -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="48dp"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:paddingHorizontal="12dp">

        <TextView
            android:id="@+id/btnBack"
            android:layout_width="36dp"
            android:layout_height="36dp"
            android:text="←"
            android:textSize="18sp"
            android:textColor="@color/text_muted"
            android:gravity="center"
            android:background="@drawable/menu_btn_bg"
            android:clickable="true" />

        <TextView
            android:id="@+id/tvPageTitle"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:layout_marginStart="10dp"
            android:textSize="16sp"
            android:textColor="@color/accent_cyan"
            android:textStyle="bold"
            android:fontFamily="monospace" />

        <TextView
            android:id="@+id/tvPageCount"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textSize="11sp"
            android:textColor="@color/text_muted"
            android:fontFamily="monospace" />
    </LinearLayout>

    <!-- Search -->
    <EditText
        android:id="@+id/etSearch"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="搜索…"
        android:textColorHint="@color/text_muted"
        android:background="@drawable/edittext_bg"
        android:padding="10dp"
        android:layout_marginHorizontal="14dp"
        android:layout_marginBottom="8dp"
        android:textSize="13sp"
        android:textColor="@color/text_primary" />

    <!-- List -->
    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/rvList"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:clipToPadding="false"
        android:paddingHorizontal="14dp" />
</LinearLayout>
```

**Step 2: Create item_expandable.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical">

    <!-- Header row (always visible) -->
    <LinearLayout
        android:id="@+id/itemHead"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:minHeight="48dp"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:paddingVertical="12dp">

        <TextView
            android:id="@+id/tvChevron"
            android:layout_width="18dp"
            android:layout_height="wrap_content"
            android:text="▶"
            android:textSize="12sp"
            android:textColor="@color/text_muted" />

        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:orientation="vertical"
            android:layout_marginStart="8dp">

            <TextView
                android:id="@+id/tvItemTitle"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:textSize="13sp"
                android:textColor="@color/text_primary"
                android:singleLine="true"
                android:ellipsize="end"
                android:fontFamily="monospace" />

            <TextView
                android:id="@+id/tvItemSub"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:textSize="10sp"
                android:textColor="@color/text_muted"
                android:layout_marginTop="2dp" />
        </LinearLayout>

        <TextView
            android:id="@+id/tvItemTag"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:paddingHorizontal="7dp"
            android:paddingVertical="2dp"
            android:textSize="9sp"
            android:textColor="@color/accent_cyan"
            android:background="@drawable/menu_btn_bg"
            android:layout_marginStart="8dp" />
    </LinearLayout>

    <!-- Expandable body (hidden by default) -->
    <LinearLayout
        android:id="@+id/itemBody"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:paddingStart="26dp"
        android:paddingBottom="14dp"
        android:visibility="gone">

        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/rvDetailFields"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:nestedScrollingEnabled="false" />

        <LinearLayout
            android:id="@+id/itemActions"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:layout_marginTop="8dp" />
    </LinearLayout>
</LinearLayout>
```

**Step 3: Commit**

```bash
git add app/src/main/res/layout/fragment_secondary.xml app/src/main/res/layout/item_expandable.xml
git commit -m "feat: add shared secondary page and expandable item layouts"
```

---

### Task 3.3: Create HistoryFragment

**Objective:** Fragment showing session history with expandable items

**Files:**
- Create: `app/src/main/java/com/example/aicontrolall/ui/HistoryFragment.kt`

**Step 1: Create HistoryFragment using fragment_secondary layout**

```kotlin
// Loads SessionStore data into RecyclerView with expandable items
// Each item shows session title, message count, timestamp
// Expand shows session ID + summary + "继续对话" / "删除" buttons
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/example/aicontrolall/ui/HistoryFragment.kt
git commit -m "feat: add HistoryFragment with session list"
```

---

### Task 3.4: Create MemoryFragment

**Files:**
- Create: `app/src/main/java/com/example/aicontrolall/ui/MemoryFragment.kt`

Loads MemoryStore data. Expand shows full content + tags + "编辑"/"删除" buttons. Search filters by FTS5/LIKE.

---

### Task 3.5: Create ToolsFragment

**Files:**
- Create: `app/src/main/java/com/example/aicontrolall/ui/ToolsFragment.kt`

Loads McpGateway.listTools(). Shows tool name, description, status tag (已注册=green, 计划中=yellow). Expand shows interface/permissions/params.

---

### Task 3.6: Create SkillsFragment

**Files:**
- Create: `app/src/main/java/com/example/aicontrolall/ui/SkillsFragment.kt`

Loads SkillStore data. Sorted by confidence desc. Expand shows version/usage/steps/pitfalls. Edit/disable buttons.

---

### Task 3.7: Create DevicesFragment

**Files:**
- Create: `app/src/main/java/com/example/aicontrolall/ui/DevicesFragment.kt`
- Create: `app/src/main/java/com/example/aicontrolall/ui/DeviceDriver.kt` (device capability model)

**Step 1: Create DeviceDriver data model**

```kotlin
data class DeviceDriver(
    val id: String,
    val name: String,          // "摄像头", "麦克风", etc.
    val type: String,          // "输入设备", "输出设备", "外设"
    val category: String,      // "内置" | "外设"
    val capabilities: List<String>,
    val status: String,
    val dataFields: List<String>,
    val mcpTool: String?,      // Associated MCP tool name, null if planned
    val mcpToolStatus: String  // "registered" | "planned"
)
```

**Step 2: Create DevicesFragment**

Shows 8 hardcoded device drivers (camera, mic, speaker, screen, gps, sensors, bluetooth-audio, bluetooth-health). Expand shows capabilities/status/data/MCP tool.

---

### Task 3.8: Create SettingsFragment

**Files:**
- Create: `app/src/main/res/layout/fragment_settings.xml`
- Create: `app/src/main/java/com/example/aicontrolall/ui/SettingsFragment.kt`

**Step 1: Create settings layout with immediate-toggle switches**

API key, model, base URL as EditText. Evolution engine as a clickable tag that toggles green↔red on tap (no save button). Config path, DB size, version as read-only. Reset button at bottom.

**Step 2: Commit**

```bash
git add app/src/main/res/layout/fragment_settings.xml \
        app/src/main/java/com/example/aicontrolall/ui/SettingsFragment.kt
git commit -m "feat: add SettingsFragment with immediate-toggle switches"
```

---

## Phase 4: MainActivity Refactor

### Task 4.1: Rewrite MainActivity

**Objective:** Wire up DrawerLayout, fragments, menu, topbar, and all navigation

**Files:**
- Modify: `app/src/main/java/com/example/aicontrolall/ui/MainActivity.kt`

**Step 1: Rewrite MainActivity**

```kotlin
class MainActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var contentFrame: FrameLayout
    private lateinit var rvMenu: RecyclerView
    private lateinit var btnHamburger: TextView
    private lateinit var tvStatusPill: TextView
    private lateinit var menuAdapter: MenuAdapter
    private lateinit var agentCore: AgentCore
    // ... stores, gateway, etc.

    private val menuItems = listOf(
        MenuItem("chat", "💬", "聊天"),
        MenuItem("history", "🕑", "历史会话"),
        MenuItem("memory", "◆", "记忆"),
        MenuItem("tools", "⚒", "工具"),
        MenuItem("skills", "★", "技能"),
        MenuItem("divider", "", "", isDivider = true),
        MenuItem("devices", "🔧", "设备"),
        MenuItem("settings", "⚙", "设置")
    )

    private val fragments = mutableMapOf<String, Fragment>()
    private var currentPage: String = "default"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Bind views
        drawerLayout = findViewById(R.id.drawerLayout)
        contentFrame = findViewById(R.id.contentFrame)
        rvMenu = findViewById(R.id.rvMenu)
        btnHamburger = findViewById(R.id.btnHamburger)
        tvStatusPill = findViewById(R.id.tvStatusPill)

        // Setup menu
        menuAdapter = MenuAdapter(menuItems.filter { !it.isDivider }) { item ->
            showPage(item.id)
            drawerLayout.closeDrawer(GravityCompat.END)
        }
        rvMenu.layoutManager = LinearLayoutManager(this)
        rvMenu.adapter = menuAdapter

        // Hamburger opens/closes drawer
        btnHamburger.setOnClickListener {
            if (drawerLayout.isDrawerOpen(GravityCompat.END))
                drawerLayout.closeDrawer(GravityCompat.END)
            else
                drawerLayout.openDrawer(GravityCompat.END)
        }

        // Initialize agent
        initializeAgent()

        // Show default page on start
        showPage("default")

        // Update status pill
        updateStatusPill()
    }

    private fun showPage(pageId: String) {
        currentPage = pageId
        val fragment = fragments.getOrPut(pageId) {
            when (pageId) {
                "default" -> DefaultPageFragment()
                "chat" -> ChatFragment()
                "history" -> HistoryFragment()
                "memory" -> MemoryFragment()
                "tools" -> ToolsFragment()
                "skills" -> SkillsFragment()
                "devices" -> DevicesFragment()
                "settings" -> SettingsFragment()
                else -> DefaultPageFragment()
            }
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.contentFrame, fragment)
            .commit()
        updateStatusPill()
    }

    fun navigateBack() {
        showPage("default")
    }

    private fun updateStatusPill() {
        val m = memoryStore.count()
        val s = skillStore.getAll(100).size
        val t = mcpGateway.listTools().size
        val d = 8 // Device count (fixed for now)
        tvStatusPill.text = "● M:$m S:$s T:$t D:$d"
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
            drawerLayout.closeDrawer(GravityCompat.END)
        } else if (currentPage != "default") {
            showPage("default")
        } else {
            super.onBackPressed()
        }
    }
}
```

**Step 2: Clean up old code**

Remove old panel-related code (openPanel, closePanel, refreshPanelData, panel views), old btnMenu.onLongClickListener (settings), and old statusPanel animations.

**Step 3: Verify build**

```bash
cd /home/lqh/projects/ai-control-all && ./gradlew assembleDebug 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/example/aicontrolall/ui/MainActivity.kt
git commit -m "refactor: rewrite MainActivity with DrawerLayout + fragment navigation"
```

---

## Phase 5: Integration & Polish

### Task 5.1: Wire ChatFragment to AgentCore

**Objective:** ChatFragment calls agentCore.processInput() and updates RecyclerView

Pass AgentCore, MemoryStore, SkillStore, SessionStore, McpGateway references from MainActivity to ChatFragment via a shared ViewModel or direct setter injection.

**Files:**
- Modify: `app/src/main/java/com/example/aicontrolall/ui/ChatFragment.kt`

---

### Task 5.2: Add expand/collapse to all secondary page adapters

**Objective:** All RecyclerView items (history/memory/tools/skills/devices) expand on tap to show detail body with action buttons

Each adapter tracks expanded state via a Set<Int> of expanded positions. Clicking toggles visibility of the detail body. Edit/delete actions call the appropriate Store method.

---

### Task 5.3: Remove old SettingsActivity

**Objective:** SettingsActivity is no longer needed; SettingsFragment replaces it

- Delete `app/src/main/java/com/example/aicontrolall/ui/SettingsActivity.kt`
- Delete `app/src/main/res/layout/activity_settings.xml`
- Update AndroidManifest.xml to remove SettingsActivity entry

---

### Task 5.4: Update AndroidManifest

Remove SettingsActivity. Keep single MainActivity with launchMode="singleTask".

---

### Task 5.5: Final verification

```bash
cd /home/lqh/projects/ai-control-all && ./gradlew assembleDebug 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL

Verify on device/emulator:
- App launches to default page with animated planet
- ☰ opens drawer menu with 7 items + dividers
- Each menu item navigates to correct page
- ← back button on secondary pages returns to default page
- Chat page sends messages and shows agent responses
- Memory/Tools/Skills pages show real data
- Settings toggle works immediately
- Status pill updates with correct counts

---

## Phase Summary

| Phase | Tasks | Description |
|-------|-------|-------------|
| 1 | 1.1–1.3 | Navigation shell (DrawerLayout + menu + topbar) |
| 2 | 2.1–2.2 | Default page (Canvas animation) |
| 3 | 3.1–3.8 | 8 fragment pages (chat + 7 secondary) |
| 4 | 4.1 | MainActivity refactor |
| 5 | 5.1–5.5 | Integration, polish, cleanup |
