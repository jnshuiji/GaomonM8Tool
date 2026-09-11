package com.gaomon.m8.xposed

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.gaomon.m8.data.DeviceRepository
import com.gaomon.m8.model.ActionType
import com.gaomon.m8.model.KeyConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

/**
 * 负责在 StarNote 的“设置” -> “手写笔设置” (SettingsStylusFragment) 中动态植入高漫 M8 顶层卡片
 * 并提供与 StarNote 原版完全一致的原生风格次级选择页面（顶栏「返回」+ 居中标题 + 圆角卡片 + 橙色对勾）
 */
object StylusSettingsInjector {

    private const val TAG = "GaomonSettingsInject"
    private const val TAG_ROOT_CARD = "gaomon_m8_settings_root_card"
    private const val TAG_SUBPAGE = "gaomon_m8_settings_subpage"
    private const val CONTAINER_ID_NAME = "common_device_stylus_container"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val attachedActivities = java.util.Collections.newSetFromMap(java.util.WeakHashMap<Activity, Boolean>())

    // 记录当前处于激活状态的主容器与次级选择页，用于返回键处理
    @Volatile
    private var currentMainContainerRef: WeakReference<View>? = null
    @Volatile
    private var currentSubpageRef: WeakReference<View>? = null

    /**
     * 处理系统返回键或返回手势，若次级选择页处于显示状态，则消费该事件并退回主设置列表
     */
    fun handleBackPress(): Boolean {
        val subpage = currentSubpageRef?.get() ?: return false
        val mainContainer = currentMainContainerRef?.get() ?: return false
        if (subpage.visibility == View.VISIBLE) {
            subpage.visibility = View.GONE
            mainContainer.visibility = View.VISIBLE
            GaomonLog.d(TAG, "Back pressed: dismissed Gaomon subpage and restored main settings container")
            return true
        }
        return false
    }

    fun injectIfPresent(activity: Activity) {
        mainHandler.post {
            try {
                findAndInjectContainer(activity)
            } catch (t: Throwable) {
                GaomonLog.e(TAG, "Error injecting Gaomon settings: ${t.message}", t)
            }
        }
    }

    fun attachToActivity(activity: Activity) {
        injectIfPresent(activity)
        mainHandler.post {
            try {
                if (attachedActivities.contains(activity)) return@post
                val decor = activity.window?.decorView ?: return@post
                decor.viewTreeObserver?.addOnGlobalLayoutListener {
                    try {
                        findAndInjectContainer(activity)
                    } catch (_: Throwable) {}
                }
                attachedActivities.add(activity)
                GaomonLog.i(TAG, "Registered OnGlobalLayoutListener on ${activity.javaClass.name}")
            } catch (t: Throwable) {
                GaomonLog.e(TAG, "Failed to register layout listener: ${t.message}", t)
            }
        }
    }

    private fun findAndInjectContainer(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return

        val containerId = activity.resources.getIdentifier(CONTAINER_ID_NAME, "id", activity.packageName)
        val container = if (containerId != 0) {
            activity.findViewById<ViewGroup>(containerId)
        } else {
            val decor = activity.window?.decorView as? ViewGroup
            decor?.let { findViewRecursively(it, CONTAINER_ID_NAME) as? ViewGroup }
        }

        if (container == null) {
            GaomonLog.d(TAG, "Container $CONTAINER_ID_NAME not found yet in ${activity.javaClass.name}")
            return
        }

        val fragmentRoot = container.parent as? ViewGroup ?: return

        // common_device_stylus_container 为 ScrollView，内部承载条目的为其唯一子 View (LinearLayout)
        val targetContainer = if (container is ScrollView) {
            if (container.childCount == 0) return
            (container.getChildAt(0) as? ViewGroup) ?: container
        } else {
            container
        }

        // 记录主容器弱引用
        currentMainContainerRef = WeakReference(container)

        // 避免重复注入主卡片
        if (targetContainer.findViewWithTag<View>(TAG_ROOT_CARD) != null) {
            return
        }

        // 确保次级选择页面已准备好
        ensureSubpageContainer(activity, fragmentRoot, container)

        val cardView = buildGaomonSettingsCard(activity, targetContainer, fragmentRoot, container)
        cardView.tag = TAG_ROOT_CARD

        // 插入在第一位（顶层选项）
        targetContainer.addView(cardView, 0)
        GaomonLog.i(TAG, "Successfully injected Gaomon M8 top-level settings card into $CONTAINER_ID_NAME")
    }

    private fun findViewRecursively(parent: ViewGroup, targetName: String): View? {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child.id != View.NO_ID) {
                try {
                    val entry = child.resources.getResourceEntryName(child.id)
                    if (entry == targetName) return child
                } catch (_: Throwable) {}
            }
            if (child is ViewGroup) {
                val found = findViewRecursively(child, targetName)
                if (found != null) return found
            }
        }
        return null
    }

    private fun buildGaomonSettingsCard(
        activity: Activity,
        targetContainer: ViewGroup,
        fragmentRoot: ViewGroup,
        mainScrollView: View
    ): View {
        val context = activity
        val keyConfig = KeyConfig(context)
        val repo = DeviceRepository(context)

        // 动态采样宿主 StarNote 现有原生卡片样式（自适应深棕/浅色主题）
        val sampleCard = findViewRecursively(targetContainer, "double_click_container")
        val sampleTitle = findViewRecursively(targetContainer, "tv_stylus_pen_double_click_name") as? TextView
        val sampleValue = findViewRecursively(targetContainer, "tv_stylus_pen_double_click_fun") as? TextView
        val sampleHeader = targetContainer.getChildAt(0) as? TextView

        val primaryTextColor = sampleTitle?.currentTextColor ?: Color.WHITE
        val secondaryTextColor = sampleValue?.currentTextColor ?: Color.argb(180, 255, 255, 255)
        val headerTextColor = sampleHeader?.currentTextColor ?: Color.argb(160, 255, 255, 255)
        val sampleBgDrawable = sampleCard?.background?.constantState?.newDrawable()?.mutate()

        // 采样原生返回键或强调色（StarNote 标志性暖橙色）
        val sampleBack = findViewRecursively(fragmentRoot, "tv_back") as? TextView
        val accentColor = sampleBack?.currentTextColor ?: Color.parseColor("#E58A6A")

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(context, 8)
                bottomMargin = dpToPx(context, 16)
            }
        }

        // 1. 分组标题: "高漫 M8 数位板 / 压感笔"
        val titleView = TextView(context).apply {
            text = "高漫 M8 数位板 / 压感笔"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(headerTextColor)
            setPadding(
                dpToPx(context, 8),
                dpToPx(context, 4),
                dpToPx(context, 8),
                dpToPx(context, 8)
            )
        }
        rootLayout.addView(titleView)

        // 2. 卡片容器
        val cardContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            background = sampleBgDrawable ?: createCardBackground(context)
            elevation = dpToPx(context, 1).toFloat()
        }

        // ── 需求项 1：数位板连接状态放在两个按键的上方 ──
        val (statusRow, statusValueTv) = createSettingRow(
            context = context,
            title = "数位板连接状态",
            currentValue = "正在检测...",
            primaryColor = primaryTextColor,
            secondaryColor = secondaryTextColor,
            hasChevron = false
        )
        fun refreshHardwareStatus() {
            statusValueTv.text = "正在检测..."
            CoroutineScope(Dispatchers.Main).launch {
                val (connected, info) = withContext(Dispatchers.IO) { repo.checkHardwareConnected() }
                if (connected) {
                    statusValueTv.text = info
                    statusValueTv.setTextColor(Color.parseColor("#4CAF50"))
                } else {
                    statusValueTv.text = "未检测到数位板"
                    statusValueTv.setTextColor(secondaryTextColor)
                }
            }
        }
        refreshHardwareStatus()
        statusRow.setOnClickListener {
            refreshHardwareStatus()
        }
        cardContainer.addView(statusRow)

        // 分割线
        cardContainer.addView(createDivider(context))

        // ── 需求项 2：主键（原下侧键，默认按住切橡皮） ──
        val (primaryRow, primaryValueTv) = createSettingRow(
            context = context,
            title = "主键",
            currentValue = getActionDisplayName(keyConfig.penLower),
            primaryColor = primaryTextColor,
            secondaryColor = secondaryTextColor,
            hasChevron = true
        )
        primaryRow.setOnClickListener {
            openSubpageForKey(
                activity = activity,
                fragmentRoot = fragmentRoot,
                mainScrollView = mainScrollView,
                isPrimary = true,
                keyConfig = keyConfig,
                summaryTv = primaryValueTv,
                primaryColor = primaryTextColor,
                accentColor = accentColor,
                cardBgDrawable = sampleBgDrawable
            )
        }
        cardContainer.addView(primaryRow)

        // 分割线
        cardContainer.addView(createDivider(context))

        // ── 需求项 3：副键（原上侧键，放在主键下方） ──
        val (secondaryRow, secondaryValueTv) = createSettingRow(
            context = context,
            title = "副键",
            currentValue = getActionDisplayName(keyConfig.penUpper),
            primaryColor = primaryTextColor,
            secondaryColor = secondaryTextColor,
            hasChevron = true
        )
        secondaryRow.setOnClickListener {
            openSubpageForKey(
                activity = activity,
                fragmentRoot = fragmentRoot,
                mainScrollView = mainScrollView,
                isPrimary = false,
                keyConfig = keyConfig,
                summaryTv = secondaryValueTv,
                primaryColor = primaryTextColor,
                accentColor = accentColor,
                cardBgDrawable = sampleBgDrawable
            )
        }
        cardContainer.addView(secondaryRow)

        rootLayout.addView(cardContainer)
        return rootLayout
    }

    private fun createSettingRow(
        context: Context,
        title: String,
        currentValue: String,
        primaryColor: Int,
        secondaryColor: Int,
        hasChevron: Boolean
    ): Pair<View, TextView> {
        val row = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(context, 56)
            )
            isClickable = true
            isFocusable = true
            background = createItemRippleBackground(context)
            setPadding(dpToPx(context, 16), 0, dpToPx(context, 16), 0)
        }

        val titleTv = TextView(context).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(primaryColor)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.START or Gravity.CENTER_VERTICAL
            )
        }
        row.addView(titleTv)

        val displayVal = if (hasChevron) "$currentValue ›" else currentValue
        val valueTv = TextView(context).apply {
            text = displayVal
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(secondaryColor)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END or Gravity.CENTER_VERTICAL
            )
        }
        row.addView(valueTv)

        return Pair(row, valueTv)
    }

    /**
     * 确保次级选择页容器已注入在 Fragment 根视图中
     */
    private fun ensureSubpageContainer(activity: Activity, fragmentRoot: ViewGroup, mainScrollView: View): ViewGroup {
        val existing = fragmentRoot.findViewWithTag<ViewGroup>(TAG_SUBPAGE)
        if (existing != null) {
            currentSubpageRef = WeakReference(existing)
            return existing
        }

        val subpageLayout = LinearLayout(activity).apply {
            tag = TAG_SUBPAGE
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        fragmentRoot.addView(subpageLayout)
        currentSubpageRef = WeakReference(subpageLayout)
        return subpageLayout
    }

    /**
     * 打开与 StarNote 原版完全一致的原生次级选择页
     */
    private fun openSubpageForKey(
        activity: Activity,
        fragmentRoot: ViewGroup,
        mainScrollView: View,
        isPrimary: Boolean,
        keyConfig: KeyConfig,
        summaryTv: TextView,
        primaryColor: Int,
        accentColor: Int,
        cardBgDrawable: android.graphics.drawable.Drawable?
    ) {
        val subpageLayout = ensureSubpageContainer(activity, fragmentRoot, mainScrollView) as LinearLayout
        subpageLayout.removeAllViews()

        val context = activity
        val keyTitle = if (isPrimary) "主键" else "副键"
        val currentAction = if (isPrimary) keyConfig.penLower else keyConfig.penUpper

        val actions = listOf(
            ActionType.TOGGLE_ERASER_HOLD,
            ActionType.SELECT_PEN,
            ActionType.SELECT_ERASER,
            ActionType.SELECT_LASSO,
            ActionType.NONE
        )

        // 1. 顶部导航栏 (高度 ~56dp，包含「返回」与居中标题)
        val topBar = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(context, 56)
            )
        }

        val backTv = TextView(context).apply {
            text = "‹ 返回"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(accentColor)
            typeface = Typeface.DEFAULT_BOLD
            isClickable = true
            isFocusable = true
            background = createItemRippleBackground(context)
            setPadding(dpToPx(context, 16), dpToPx(context, 10), dpToPx(context, 16), dpToPx(context, 10))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.START or Gravity.CENTER_VERTICAL
            ).apply {
                leftMargin = dpToPx(context, 8)
            }
            setOnClickListener {
                subpageLayout.visibility = View.GONE
                mainScrollView.visibility = View.VISIBLE
            }
        }
        topBar.addView(backTv)

        val titleTv = TextView(context).apply {
            text = keyTitle
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTextColor(primaryColor)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }
        topBar.addView(titleTv)
        subpageLayout.addView(topBar)

        // 细分割线
        val topDivider = View(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                1
            ).apply {
                leftMargin = dpToPx(context, 24)
                rightMargin = dpToPx(context, 24)
            }
            setBackgroundColor(Color.argb(25, 255, 255, 255))
        }
        subpageLayout.addView(topDivider)

        // 2. 列表滚动容器
        val scrollContent = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                leftMargin = dpToPx(context, 24)
                rightMargin = dpToPx(context, 24)
                topMargin = dpToPx(context, 20)
                bottomMargin = dpToPx(context, 20)
            }
        }

        // 3. 原生风格圆角卡片
        val optionsCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            background = cardBgDrawable?.constantState?.newDrawable()?.mutate() ?: createCardBackground(context)
        }

        val checkmarkViews = mutableMapOf<ActionType, TextView>()

        for ((index, action) in actions.withIndex()) {
            val itemRow = FrameLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dpToPx(context, 56)
                )
                isClickable = true
                isFocusable = true
                background = createItemRippleBackground(context)
                setPadding(dpToPx(context, 20), 0, dpToPx(context, 20), 0)
            }

            val itemLabel = TextView(context).apply {
                text = getActionDisplayName(action)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(primaryColor)
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.START or Gravity.CENTER_VERTICAL
                )
            }
            itemRow.addView(itemLabel)

            // 右侧橙色对勾标记 ✓
            val checkmark = TextView(context).apply {
                text = "✓"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setTextColor(accentColor)
                typeface = Typeface.DEFAULT_BOLD
                visibility = if (action == currentAction) View.VISIBLE else View.GONE
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.END or Gravity.CENTER_VERTICAL
                )
            }
            itemRow.addView(checkmark)
            checkmarkViews[action] = checkmark

            itemRow.setOnClickListener {
                // 更新选中状态
                checkmarkViews.values.forEach { it.visibility = View.GONE }
                checkmark.visibility = View.VISIBLE

                if (isPrimary) {
                    keyConfig.penLower = action
                    GaomonSettingsState.updateLowerAction(action)
                } else {
                    keyConfig.penUpper = action
                    GaomonSettingsState.updateUpperAction(action)
                }

                summaryTv.text = "${getActionDisplayName(action)} ›"
            }

            optionsCard.addView(itemRow)

            if (index < actions.size - 1) {
                optionsCard.addView(createDivider(context))
            }
        }

        scrollContent.addView(optionsCard)
        subpageLayout.addView(scrollContent)

        // 显隐切换：隐藏主设置列表，展开原生次级选择页
        mainScrollView.visibility = View.GONE
        subpageLayout.visibility = View.VISIBLE
    }

    private fun getActionDisplayName(action: ActionType): String {
        return when (action) {
            ActionType.TOGGLE_ERASER_HOLD -> "按住切橡皮，松开回画笔"
            ActionType.SELECT_PEN -> "画笔"
            ActionType.SELECT_ERASER -> "橡皮擦"
            ActionType.SELECT_LASSO -> "套索"
            ActionType.NONE -> "无操作"
        }
    }

    private fun createDivider(context: Context): View {
        return View(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                1
            ).apply {
                leftMargin = dpToPx(context, 16)
                rightMargin = dpToPx(context, 16)
            }
            setBackgroundColor(Color.argb(20, 255, 255, 255))
        }
    }

    private fun createCardBackground(context: Context): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(context, 16).toFloat()
            setColor(Color.argb(32, 255, 255, 255))
        }
    }

    private fun createItemRippleBackground(context: Context): RippleDrawable {
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(context, 12).toFloat()
            setColor(Color.WHITE)
        }
        val rippleColor = ColorStateList.valueOf(Color.argb(40, 255, 255, 255))
        return RippleDrawable(rippleColor, null, mask)
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density + 0.5f).toInt()
    }
}
