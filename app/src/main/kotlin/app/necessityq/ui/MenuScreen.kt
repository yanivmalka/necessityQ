package app.necessityq.ui

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.animation.AnimatedContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import app.necessityq.ui.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.necessityq.domain.Dish
import app.necessityq.domain.FamilyMember
import app.necessityq.domain.MealData
import app.necessityq.domain.MealPlanner
import app.necessityq.domain.MealSlot
import app.necessityq.domain.ResolvedMeal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Pages inside the menu section (a small back stack; the system back button walks it). */
private sealed interface MenuPage {
    data object Home : MenuPage
    data object Search : MenuPage
    data class Recipe(val dishId: String) : MenuPage
    data class EditRecipe(val dishId: String?) : MenuPage
}

private val shortDate = DateTimeFormatter.ofPattern("d.M")

/**
 * Meal planning: the family (profiles and ratings), a calendar one or two weeks ahead on top of a weekly or
 * bi-weekly cycle, "cooked" tracking, recipes (search, detail, servings), and menu → shopping list.
 */
@Composable
fun MenuScreen(
    vm: AppViewModel,
    modifier: Modifier = Modifier,
    /** Told whether a page (a recipe, the search…) is open over the menu, so swiping does not switch tabs there. */
    onInnerPage: (Boolean) -> Unit = {},
) {
    val stack = remember { mutableStateListOf<MenuPage>() }
    fun push(page: MenuPage) { stack.add(page) }
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    BackHandler(enabled = stack.isNotEmpty()) { pop() }
    val inner = stack.isNotEmpty()
    val latestOnInnerPage by rememberUpdatedState(onInnerPage)
    LaunchedEffect(inner) { latestOnInnerPage(inner) }
    DisposableEffect(Unit) { onDispose { latestOnInnerPage(false) } }

    // each page opens sliding in from the left and closes sliding back out, as the app's other pages
    AnimatedContent(
        targetState = stack.toList(),
        transitionSpec = { pageSlide(back = targetState.size < initialState.size) },
        contentKey = { it.lastOrNull() ?: MenuPage.Home },
        label = "menuPage",
    ) { pages ->
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (val page = pages.lastOrNull() ?: MenuPage.Home) {
                MenuPage.Home -> MenuHome(vm, modifier, onOpen = { push(it) })
                MenuPage.Search -> RecipeSearchScreen(vm, onBack = { pop() }, onOpen = { push(MenuPage.Recipe(it)) }, modifier = modifier)
                is MenuPage.Recipe -> RecipeScreen(vm, page.dishId, onBack = { pop() }, onEdit = { push(MenuPage.EditRecipe(page.dishId)) }, modifier = modifier)
                is MenuPage.EditRecipe -> RecipeEditScreen(
                    vm, page.dishId,
                    onBack = { pop() },
                    onSaved = { id -> pop(); if (page.dishId == null) push(MenuPage.Recipe(id)) },
                    modifier = modifier,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MenuHome(vm: AppViewModel, modifier: Modifier, onOpen: (MenuPage) -> Unit) {
    val data by vm.meals.collectAsStateWithLifecycle()
    val purchases by vm.purchases.collectAsStateWithLifecycle()
    val listSnapshot by vm.snapshot.collectAsStateWithLifecycle()
    val listLines = listSnapshot?.lines.orEmpty()
    val today = remember { LocalDate.now() }
    val todayDay = today.toEpochDay()
    val horizon = 7 * data.plan.cycleWeeks
    var selected by rememberSaveable { mutableLongStateOf(todayDay) }
    var picking by remember { mutableStateOf<Pair<Long, MealSlot>?>(null) }
    var showAllRecipes by remember { mutableStateOf(false) }
    /** Recipes ticked for deletion; null = not in selection mode. */
    var picked by remember { mutableStateOf<Set<String>?>(null) }
    var confirmBulkDelete by remember { mutableStateOf(false) }
    BackHandler(enabled = picked != null) { picked = null }
    LaunchedEffect(horizon) { if (selected < todayDay || selected >= todayDay + horizon) selected = todayDay }
    val dishes = data.dishes.associateBy { it.id }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            navigationIcon = { BackNavButton() },
            title = { Text("התפריט", style = MaterialTheme.typography.titleLarge) },
            actions = { IconButton(onClick = { onOpen(MenuPage.Search) }) { Icon(Icons.Default.Search, contentDescription = "חיפוש מתכון") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        Column(
            Modifier.fadingVerticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---- planning ahead ----
            MenuCard("תכנון קדימה") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(1, 2).forEachIndexed { i, weeks ->
                        SegmentedButton(
                            selected = data.plan.cycleWeeks == weeks,
                            onClick = {
                                vm.updateMeals {
                                    val plan = it.plan.copy(cycleWeeks = weeks, meals = it.plan.meals.filter { m -> m.day < weeks * 7 })
                                    it.copy(plan = if (weeks == 2 && it.plan.anchorEpochDay == null) MealPlanner.anchorThisWeek(plan, today) else plan)
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                            icon = {},
                        ) { Text(if (weeks == 1) "שבוע קדימה" else "שבועיים קדימה") }
                    }
                }
                if (data.plan.cycleWeeks == 2) {
                    val thisWeekA = MealPlanner.cycleDay(data.plan, MealPlanner.sundayOf(today).toEpochDay()) < 7
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "מחזור דו-שבועי · השבוע הוא שבוע ${if (thisWeekA) "א'" else "ב'"}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            vm.updateMeals {
                                val sunday = MealPlanner.sundayOf(today).toEpochDay()
                                it.copy(plan = it.plan.copy(anchorEpochDay = if (thisWeekA) sunday - 7 else sunday))
                            }
                        }) { Text("החלפה") }
                    }
                }

                // date strip
                val listState = rememberLazyListState()
                LazyRow(Modifier.fadingEdges(listState, horizontal = true), state = listState, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items((0 until horizon).map { today.plusDays(it.toLong()) }, key = { it.toEpochDay() }) { date ->
                        val meals = MealPlanner.mealsOn(data, date.toEpochDay())
                        DayChip(date, isToday = date == today, selected = date.toEpochDay() == selected, meals = meals) { selected = date.toEpochDay() }
                    }
                }

                val date = LocalDate.ofEpochDay(selected)
                Text(
                    (if (date == today) "היום · " else "") + "יום ${MealPlanner.dayNames[MealPlanner.weekdayIndex(date)]}, ${date.format(shortDate)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp),
                )
                val meals = MealPlanner.mealsOn(data, selected)
                val slots = MealPlanner.daySlots + meals.map { it.slot }.filter { it !in MealPlanner.daySlots }.distinct()
                slots.forEach { slot ->
                    val meal = meals.firstOrNull { it.slot == slot }
                    val slotDish = meal?.let { dishes[it.dishId] }
                    SlotRow(
                        slot = slot,
                        meal = meal,
                        dish = slotDish,
                        members = data.members,
                        onList = slotDish != null && app.necessityq.domain.RecipeListSync.onList(listLines, slotDish).isNotEmpty(),
                        onAddToList = {
                            if (slotDish != null && meal != null) vm.addRecipeToList(slotDish.id, meal.servings ?: slotDish.plannedServings)
                        },
                        onPick = { picking = selected to slot },
                        onOpenRecipe = { id -> onOpen(MenuPage.Recipe(id)) },
                        onToggleCooked = { cooked -> vm.updateMeals { MealPlanner.setCooked(it, selected, slot, cooked) } },
                    )
                    HorizontalDivider()
                }
                val planned = (todayDay until todayDay + horizon).sumOf { d -> MealPlanner.mealsOn(data, d).count { !it.cooked } }
                Button(onClick = { vm.addMenuToList(todayDay, horizon) }, modifier = Modifier.fillMaxWidth(), enabled = planned > 0) {
                    Icon(Icons.Default.ShoppingCart, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text(if (planned > 0) "מרכיבים לרשימה · $planned ארוחות ב-$horizon הימים הקרובים" else "אין עדיין ארוחות מתוכננות")
                }
            }

            // ---- tracking ----
            val cooked = MealPlanner.recentlyCooked(data, today, 14)
            if (cooked.isNotEmpty()) {
                MenuCard("מה בושל לאחרונה") {
                    cooked.take(8).forEach { m ->
                        val d = LocalDate.ofEpochDay(m.epochDay)
                        Row(Modifier.fillMaxWidth().clickable { onOpen(MenuPage.Recipe(m.dishId)) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(dishes[m.dishId]?.name.orEmpty(), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("${MealPlanner.shortDayNames[MealPlanner.weekdayIndex(d)]} ${d.format(shortDate)} · ${m.slot.label}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // ---- recipes ----
            MenuCard("המתכונים שלי") {
                Surface(
                    onClick = { onOpen(MenuPage.Search) },
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Search, null)
                        Spacer(Modifier.width(10.dp))
                        Text("חיפוש מתכון — שלי או ממאגר צמרת", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (data.dishes.isEmpty()) {
                    OutlinedButton(onClick = { vm.updateMeals { it.copy(dishes = MealPlanner.sampleDishes) } }) { Text("טעינת מתכונים לדוגמה") }
                }
                val sorted = data.dishes.sortedBy { it.name }
                // selection mode: long-press a recipe (or "בחירה") to tick several and delete them together
                val selecting = picked != null
                if (selecting) {
                    val chosen = picked.orEmpty()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("נבחרו ${chosen.size}", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = {
                            picked = if (chosen.size == sorted.size) emptySet() else sorted.map { it.id }.toSet()
                        }) { Text(if (chosen.size == sorted.size) "ניקוי" else "בחירת הכל") }
                        TextButton(onClick = { picked = null }) { Text("ביטול") }
                    }
                }
                (if (showAllRecipes || selecting) sorted else sorted.take(6)).forEach { dish ->
                    RecipeRow(
                        dish, data,
                        checked = picked?.contains(dish.id),
                        onLongClick = { picked = picked.orEmpty() + dish.id },
                    ) {
                        val chosen = picked
                        if (chosen == null) onOpen(MenuPage.Recipe(dish.id))
                        else picked = if (dish.id in chosen) chosen - dish.id else chosen + dish.id
                    }
                }
                Row {
                    if (selecting) {
                        Button(
                            onClick = { confirmBulkDelete = true },
                            enabled = !picked.isNullOrEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        ) {
                            Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("מחיקה (${picked.orEmpty().size})")
                        }
                    } else {
                        if (sorted.size > 6) {
                            TextButton(onClick = { showAllRecipes = !showAllRecipes }) { Text(if (showAllRecipes) "פחות" else "כל המתכונים (${sorted.size})") }
                        }
                        Spacer(Modifier.weight(1f))
                        if (sorted.isNotEmpty()) TextButton(onClick = { picked = emptySet() }) { Text("בחירה") }
                        TextButton(onClick = { onOpen(MenuPage.EditRecipe(null)) }) { Text("+ מתכון חדש") }
                    }
                }
            }

            // ---- reverse: from what was bought ----
            val recent = purchases.filter { System.currentTimeMillis() - it.boughtAtMillis < 14L * 86_400_000 }.map { it.itemId }.toSet()
            val ideas = MealPlanner.fromBought(data, recent).take(5)
            if (ideas.isNotEmpty()) {
                MenuCard("מה אפשר לבשל ממה שנקנה") {
                    ideas.forEach { (dish, share) ->
                        Text(
                            "${dish.name} — יש ${(share * 100).toInt()}% מהמרכיבים",
                            modifier = Modifier.fillMaxWidth().clickable { onOpen(MenuPage.Recipe(dish.id)) }.padding(vertical = 4.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    picking?.let { (day, slot) ->
        PickDishDialog(vm, data, day, slot, onDismiss = { picking = null }, onSearch = { picking = null; onOpen(MenuPage.Search) })
    }
    if (confirmBulkDelete) {
        val ids = picked.orEmpty()
        AlertDialog(
            onDismissRequest = { confirmBulkDelete = false },
            title = { Text(if (ids.size == 1) "למחוק מתכון אחד?" else "למחוק ${ids.size} מתכונים?") },
            text = { Text("המתכונים יוסרו גם מהתפריט ומהדירוגים. מה שכבר ברשימת הקניות נשאר.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmBulkDelete = false
                    picked = null
                    vm.updateMeals { d -> ids.fold(d) { acc, id -> acc.withoutDish(id) } }
                }) { Text("מחיקה") }
            },
            dismissButton = { TextButton(onClick = { confirmBulkDelete = false }) { Text("ביטול") } },
        )
    }
}

@Composable
private fun DayChip(date: LocalDate, isToday: Boolean, selected: Boolean, meals: List<ResolvedMeal>, onClick: () -> Unit) {
    val shabbat = date.dayOfWeek == java.time.DayOfWeek.FRIDAY || date.dayOfWeek == java.time.DayOfWeek.SATURDAY
    val bg = when {
        selected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.primaryContainer
        shabbat -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val border = if (selected || isToday) Color.Transparent else NqTheme.colors.hairline
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val dot = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
    Column(
        Modifier.width(52.dp).height(68.dp).clip(RoundedCornerShape(14.dp)).background(bg)
            .border(1.dp, border, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            MealPlanner.shortDayNames[MealPlanner.weekdayIndex(date)],
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) fg else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("${date.dayOfMonth}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = fg)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(8.dp), verticalAlignment = Alignment.CenterVertically) {
            meals.take(3).forEach { m ->
                Box(Modifier.size(5.dp).background(if (m.cooked) dot.copy(alpha = 0.4f) else dot, CircleShape))
            }
        }
    }
}

@Composable
private fun SlotRow(
    slot: MealSlot,
    meal: ResolvedMeal?,
    dish: Dish?,
    members: List<FamilyMember>,
    onList: Boolean,
    onAddToList: () -> Unit,
    onPick: () -> Unit,
    onOpenRecipe: (String) -> Unit,
    onToggleCooked: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onPick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(slot.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(64.dp))
        Column(Modifier.weight(1f)) {
            if (dish == null || meal == null) {
                Text("+ בחירת מאכל", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            } else {
                Text(
                    dish.name,
                    style = ProductNameStyle.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
                    textDecoration = if (meal.cooked) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                )
                val who = if (meal.audience.isEmpty()) "כולם" else members.filter { it.id in meal.audience }.joinToString { it.name }
                Text(
                    listOfNotNull("${meal.servings ?: dish.plannedServings} מנות", who, if (meal.dated) "רק בתאריך הזה" else null).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // where the meal stands with the shopping list: on it (kept in step with servings), or one tap away
                if (!meal.cooked) {
                    if (onList) {
                        Text(
                            "ברשימה · מתעדכן",
                            Modifier.padding(top = 4.dp).clip(NqShapes.Pill).background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    } else {
                        TextButton(onClick = onAddToList, contentPadding = PaddingValues(horizontal = 0.dp)) {
                            Text("+ מרכיבים לרשימה", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        if (dish != null && meal != null) {
            IconButton(onClick = { onOpenRecipe(dish.id) }) { Icon(Icons.Default.Restaurant, contentDescription = "פתיחת המתכון") }
            IconButton(onClick = { onToggleCooked(!meal.cooked) }) {
                Icon(
                    if (meal.cooked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (meal.cooked) "בושל — ביטול" else "סימון שבושל",
                    tint = if (meal.cooked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

/** A recipe in "המתכונים שלי"; [checked] non-null shows a selection checkbox. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecipeRow(dish: Dish, data: MealData, checked: Boolean? = null, onLongClick: () -> Unit = {}, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (checked != null) {
            Checkbox(checked = checked, onCheckedChange = { onClick() })
            Spacer(Modifier.width(4.dp))
        }
        DishTile(dish.name, dish.id, size = 40)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(dish.name, fontWeight = FontWeight.Bold)
            Text(
                "${dish.plannedServings} מנות · ${dish.ingredients.size} מרכיבים",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val stars = data.members.mapNotNull { data.rating(it.id, dish.id) }.filter { it > 0 }
        if (stars.isNotEmpty()) {
            Icon(Icons.Default.Star, null, tint = androidx.compose.ui.graphics.Color(0xFFF5A623), modifier = Modifier.size(16.dp))
            Text(String.format(java.util.Locale.US, "%.1f", stars.average()), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun MenuCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = NqShapes.Tile,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, NqTheme.colors.hairline),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

/**
 * Choose a dish for one slot on one date: who eats (ranked by their stars, dislikes and flavour), how many servings,
 * and whether it repeats in the cycle or is for this date only.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PickDishDialog(vm: AppViewModel, data: MealData, epochDay: Long, slot: MealSlot, onDismiss: () -> Unit, onSearch: () -> Unit) {
    val current = MealPlanner.mealsOn(data, epochDay).firstOrNull { it.slot == slot }
    var audience by remember { mutableStateOf(current?.audience ?: emptySet()) }
    var servings by remember { mutableStateOf(current?.servings) }
    var onlyThisDate by remember { mutableStateOf(current?.dated ?: false) }
    var showAll by remember { mutableStateOf(false) }
    val ranked = MealPlanner.rank(data, audience)
    val date = LocalDate.ofEpochDay(epochDay)
    val defaultServings = when {
        audience.isNotEmpty() -> audience.size
        data.members.isNotEmpty() -> data.members.size
        else -> Dish.DEFAULT_SERVINGS
    }

    fun choose(dishId: String) {
        vm.updateMeals { MealPlanner.setMeal(it, epochDay, slot, dishId, audience, servings, onlyThisDate) }
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${slot.label} · יום ${MealPlanner.dayNames[MealPlanner.weekdayIndex(date)]} ${date.format(shortDate)}") },
        text = {
            Column(Modifier.fadingVerticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (data.members.isNotEmpty()) {
                    Text("מי אוכל (בלי בחירה = כולם)", style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        data.members.forEach { m ->
                            FilterChip(
                                selected = m.id in audience,
                                onClick = { audience = if (m.id in audience) audience - m.id else audience + m.id },
                                label = { Text(m.name) },
                                leadingIcon = { MemberAvatar(m, size = 22.dp) },
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("מנות", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    val s = servings
                    if (s == null) {
                        TextButton(onClick = { servings = defaultServings }) { Text("לפי המתכון · שינוי") }
                    } else {
                        ServingsStepper(s, onChange = { servings = it })
                        IconButton(onClick = { servings = null }) { Icon(Icons.Default.Close, contentDescription = "לפי המתכון") }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = !onlyThisDate,
                        onClick = { onlyThisDate = false },
                        label = { Text(if (data.plan.cycleWeeks == 2) "קבוע, כל שבועיים" else "קבוע, כל שבוע") },
                    )
                    FilterChip(selected = onlyThisDate, onClick = { onlyThisDate = true }, label = { Text("רק ב-${date.format(shortDate)}") })
                }
                Text("מאכלים מומלצים", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                if (ranked.isEmpty()) Text("אין מאכלים מתאימים. אפשר לחפש מתכון או להוסיף אחד.")
                (if (showAll) ranked else ranked.take(6)).forEach { (dish, score) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { choose(dish.id) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DishTile(dish.name, dish.id, size = 32)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            dish.name,
                            fontWeight = if (dish.id == current?.dishId) FontWeight.ExtraBold else FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        // who in the family likes it (4 stars and up), from the ratings everyone keeps in sync
                        val likers = data.members.filter { (data.rating(it.id, dish.id) ?: 0) >= 4 }
                        if (likers.isNotEmpty()) {
                            // slightly overlapping, as the avatars elsewhere in the app
                            Box(Modifier.padding(end = 6.dp).semantics { contentDescription = "אוהבים: " + likers.joinToString { it.name } }) {
                                likers.take(3).forEachIndexed { i, m -> MemberAvatar(m, size = 22.dp, modifier = Modifier.padding(start = (i * 14).dp)) }
                            }
                        }
                        if (score > 0) Text("+$score", style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End)
                        if (dish.id == current?.dishId) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                }
                if (!showAll && ranked.size > 6) TextButton(onClick = { showAll = true }) { Text("עוד…") }
                TextButton(onClick = onSearch) { Icon(Icons.Default.Search, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("חיפוש מתכון נוסף") }
            }
        },
        confirmButton = {
            if (current != null) TextButton(onClick = { choose(current.dishId) }) { Text("שמירה") }
            else TextButton(onClick = onDismiss) { Text("סגירה") }
        },
        dismissButton = {
            if (current != null) {
                TextButton(onClick = {
                    vm.updateMeals { MealPlanner.setMeal(it, epochDay, slot, null, emptySet(), null, onlyThisDate) }
                    onDismiss()
                }) { Text(if (onlyThisDate) "בלי ארוחה בתאריך הזה" else "הסרה מהמחזור") }
            }
        },
    )
}
