package app.necessityq.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import app.necessityq.domain.RecipeListPreview
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import app.necessityq.ui.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.necessityq.domain.Dish
import app.necessityq.domain.Flavor
import app.necessityq.domain.HebrewStem
import app.necessityq.domain.LineStatus
import app.necessityq.domain.MealPlanner
import app.necessityq.domain.NutritionFood
import app.necessityq.domain.RecipeListSync
import app.necessityq.domain.Recipes
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

private val dateFmt = DateTimeFormatter.ofPattern("d.M")

/** − value + */
@Composable
fun ServingsStepper(value: Int, onChange: (Int) -> Unit, min: Int = 1, max: Int = 40) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = { onChange((value - 1).coerceAtLeast(min)) }, enabled = value > min) {
            Icon(Icons.Default.Remove, contentDescription = "פחות מנות")
        }
        Text(
            "$value",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.width(48.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        FilledTonalIconButton(onClick = { onChange((value + 1).coerceAtMost(max)) }, enabled = value < max) {
            Icon(Icons.Default.Add, contentDescription = "יותר מנות")
        }
    }
}

/** Square tile with the dish's first letter, tinted by its id. */
@Composable
fun DishTile(name: String, id: String, size: Int = 48) {
    Box(
        Modifier.size(size.dp).background(avatarColor(id), RoundedCornerShape((size / 3).dp)),
        contentAlignment = Alignment.Center,
    ) { Text(name.trim().take(1).ifEmpty { "?" }, fontSize = (size * 0.45).sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF3A3A3A)) }
}

@Composable
private fun DetailCard(title: String? = null, content: @Composable () -> Unit) {
    Card(
        shape = NqShapes.Tile,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, NqTheme.colors.hairline),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            title?.let { Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            content()
        }
    }
}

/**
 * A recipe in full, as in the meal-planning design: for how many (all amounts follow), the ingredients with what each
 * means for the list ("לקנייה", "יש בבית", "מתווסף לקיים", "ברשימה"), editing amounts in place, the method as steps,
 * the family's stars and where it is on the menu. Adding opens a review of what goes on the list; once the recipe is
 * there, changing servings updates the list by itself.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecipeScreen(vm: AppViewModel, dishId: String, onBack: () -> Unit, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    val data by vm.meals.collectAsStateWithLifecycle()
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val nutrition by vm.nutrition.collectAsStateWithLifecycle()
    val dish = data.dish(dishId)
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(dish?.nutritionCode) { if (dish?.nutritionCode != null) vm.loadNutrition() }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה") } },
            title = { Text(dish?.name ?: "מתכון", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            actions = {
                if (dish != null) {
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "עריכת המתכון: שם, תפוקה ואופן הכנה") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, contentDescription = "מחיקת המתכון") }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        if (dish == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        var servings by remember(dish.id) { mutableIntStateOf(dish.plannedServings) }
        var tab by rememberSaveable(dish.id) { mutableIntStateOf(0) }
        var editing by rememberSaveable(dish.id) { mutableStateOf(false) }
        var reviewOpen by remember { mutableStateOf(false) }
        val doneSteps = remember(dish.id) { mutableStateListOf<Int>() }
        val removed = remember(dish.id) { mutableStateListOf<String>() }
        val lines = snapshot?.lines.orEmpty()
        val onList = RecipeListSync.onList(lines, dish)
        val recipeOnList = onList.isNotEmpty()
        val openLines = lines.filter { it.status == LineStatus.NEEDED }.associateBy { it.itemId }
        val ownIds = onList.map { it.itemId }.toSet()
        val steps = remember(dish.instructions) { Recipes.steps(dish.instructions) }
        var importingMethod by remember(dish.id) { mutableStateOf(false) }
        if (importingMethod) ImportMethodSheet(vm, dish, onDismiss = { importingMethod = false })

        // servings are remembered on the dish; once the recipe is on the list they also update the list (after a short pause)
        LaunchedEffect(servings) {
            val listed = recipeOnList && dish.listServings != null
            if (servings == dish.plannedServings && (!listed || dish.listServings == servings)) return@LaunchedEffect
            delay(700)
            if (listed) vm.addRecipeToList(dish.id, servings) else vm.editDish(dish.id) { it.copy(cookServings = servings) }
        }

        Column(
            Modifier.weight(1f).fadingVerticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val stars = data.members.mapNotNull { data.rating(it.id, dish.id) }.filter { it > 0 }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                dish.flavor?.let { InfoPill(it.label) }
                if (stars.isNotEmpty()) InfoPill("★ " + String.format(java.util.Locale.US, "%.1f", stars.average()))
                if (dish.nutritionCode != null) InfoPill("ממאגר צמרת")
            }
            dish.nutritionCode?.let { nutrition?.food(it) }?.summary()?.takeIf { it.isNotEmpty() }?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            ServingsCard(
                servings = servings,
                recipeYield = dish.servings,
                familySize = data.members.size,
                onChange = { servings = it.coerceIn(1, 40) },
            )

            SegmentedTabs(
                labels = listOf("מרכיבים · ${dish.ingredients.size}", "אופן הכנה · ${steps.size} שלבים"),
                selected = tab,
                onSelect = { tab = it },
            )

            if (tab == 0) {
                IngredientsSection(
                    dish = dish,
                    servings = servings,
                    editing = editing,
                    onToggleEdit = { editing = !editing },
                    pantry = data.pantry,
                    buy = data.buy,
                    openLines = openLines,
                    ownIds = ownIds,
                    removed = removed,
                    onToggleHome = { id, home -> vm.setAtHome(id, home) },
                    onAdjust = { i, dir -> vm.editDish(dish.id) { app.necessityq.domain.RecipeEdits.adjust(it, i, servings, dir) } },
                    onRemove = { i ->
                        dish.ingredients.getOrNull(i)?.let { removed.add(it) }
                        vm.editDish(dish.id) { app.necessityq.domain.RecipeEdits.remove(it, i) }
                    },
                    onRestore = { text ->
                        removed.remove(text)
                        vm.editDish(dish.id) { it.copy(ingredients = it.ingredients + text) }
                    },
                    onAdd = { typed -> vm.editDish(dish.id) { app.necessityq.domain.RecipeEdits.add(it, typed, servings) } },
                )
            } else {
                StepsSection(
                    steps = steps,
                    done = doneSteps,
                    onToggle = { i -> if (i in doneSteps) doneSteps.remove(i) else doneSteps.add(i) },
                    onWrite = onEdit,
                    onImport = { importingMethod = true },
                )
            }

            // ---- family stars ----
            if (data.members.isNotEmpty()) {
                DetailCard("מה המשפחה חושבת") {
                    data.members.forEach { m ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MemberAvatar(m, size = 36.dp)
                            Column {
                                Text(m.name + if (m.linkedUserId == vm.me) " (אני)" else "", fontWeight = FontWeight.Bold)
                                StarsRow(data.rating(m.id, dish.id), onChange = { s -> vm.updateMeals { it.withRating(m.id, dish.id, s) } }, starSize = 20.dp)
                            }
                        }
                        if (MealPlanner.dislikes(m, dish)) {
                            Text("⚠ מכיל משהו ש${m.name} לא אוכל/ת", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // ---- on the menu ----
            val today = remember { LocalDate.now() }
            val upcoming = (0 until 14).flatMap { i ->
                val day = today.plusDays(i.toLong())
                MealPlanner.mealsOn(data, day.toEpochDay()).filter { it.dishId == dish.id }.map { day to it }
            }
            if (upcoming.isNotEmpty()) {
                DetailCard("בתפריט") {
                    upcoming.forEach { (day, meal) ->
                        Text("יום ${MealPlanner.dayNames[MealPlanner.weekdayIndex(day)]} ${day.format(dateFmt)} · ${meal.slot.label}" + if (meal.cooked) " · בושל ✓" else "")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // ---- bottom: into the list, or how it is on the list ----
        Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp, vertical = 10.dp)) {
            if (!recipeOnList) {
                val preview = RecipeListPreview.of(lines, dish, servings, data.pantry, data.buy)
                val fresh = preview.count { it.kind == RecipeListPreview.Kind.NEW }
                val merged = preview.count { it.kind == RecipeListPreview.Kind.MERGE }
                val enabled = preview.any { it.kind != RecipeListPreview.Kind.AT_HOME }
                Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().height(56.dp).clip(NqShapes.Field)
                        .background(if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(enabled = enabled, role = Role.Button, onClick = { if (dish.listMemory != null) vm.addRecipeToList(dish.id, servings) else reviewOpen = true })
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val ink = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    Column(Modifier.weight(1f)) {
                        Text("הוספה לרשימת הקניות", color = ink, fontWeight = FontWeight.SemiBold)
                        Text(
                            when {
                                !enabled -> "אין מה להוסיף: הכל בבית"
                                dish.listMemory != null -> "אותם מוצרים כמו בפעם הקודמת · $servings מנות"
                                else -> "$fresh חדשים · $merged יתווספו לקיימים · $servings מנות"
                            },
                            color = ink.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = ink)
                }
                    if (dish.listMemory != null && enabled) {
                        TextButton(onClick = { reviewOpen = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text("שינוי הבחירות (סוג ואריזה)")
                        }
                    }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().clip(NqShapes.Field).background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "ברשימה · ${onList.size} פריטים · ${dish.listServings ?: servings} מנות",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "שינוי מנות כאן מעדכן את הכמויות ברשימה",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    TextButton(onClick = { vm.removeRecipeFromList(dish.id) }) { Text("הסרה") }
                }
            }
        }

        if (reviewOpen) {
            RecipeAddTile(
                dish = dish,
                servings = servings,
                lines = lines,
                data = data,
                onConfirm = { chosen, variants, packs ->
                    reviewOpen = false
                    vm.addRecipeToList(dish.id, servings, only = chosen, variants = variants, packs = packs)
                },
                onDismiss = { reviewOpen = false },
            )
        }
    }

    if (confirmDelete && dish != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("למחוק את ${dish.name}?") },
            text = { Text("המתכון יוסר גם מהתפריט. מה שכבר ברשימת הקניות נשאר.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onBack(); vm.updateMeals { it.withoutDish(dish.id) } }) { Text("מחיקה") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("ביטול") } },
        )
    }
}

@Composable
private fun InfoPill(text: String) {
    Text(
        text,
        Modifier.clip(NqShapes.Pill).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, NqTheme.colors.hairline, NqShapes.Pill).padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodySmall,
    )
}

/** Two or more tabs in one segmented switch. */
@Composable
fun SegmentedTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(NqShapes.Field).background(NqTheme.colors.segmentTrack).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (on) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(i) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** "בשביל כמה?": − n + and quick choices (2, as written, the whole family, double). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServingsCard(servings: Int, recipeYield: Int, familySize: Int, onChange: (Int) -> Unit) {
    val presets = buildList {
        add(2); add(recipeYield)
        if (familySize > 0) add(familySize)
        add(recipeYield * 2)
    }.filter { it in 1..40 }.distinct().sorted()
    DetailCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("בשביל כמה?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val k = servings.toDouble() / recipeYield.coerceAtLeast(1)
                Text(
                    if (servings == recipeYield) "המתכון המקורי הוא ל-$recipeYield מנות"
                    else "המתכון המקורי ל-$recipeYield · הכמויות ×" + app.necessityq.domain.Quantity.formatNumber(k),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ServingsStepper(servings, onChange = onChange)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            presets.forEach { n ->
                val label = when (n) {
                    recipeYield -> "$n · המקורי"
                    familySize -> "$n · כל המשפחה"
                    recipeYield * 2 -> "$n · כפול"
                    else -> "$n"
                }
                FilterChip(selected = servings == n, onClick = { onChange(n) }, label = { Text(label) })
            }
        }
    }
}

private enum class IngredientState(val label: String) { BUY("לקנייה"), AT_HOME("יש בבית"), MERGE("מתווסף לקיים"), ON_LIST("ברשימה") }

/**
 * The ingredients for the chosen servings. Each shows what it means for the list; tapping that badge switches
 * "יש בבית" on or off (remembered for every recipe). In edit mode: + / − by one step, remove, and add a new one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IngredientsSection(
    dish: Dish,
    servings: Int,
    editing: Boolean,
    onToggleEdit: () -> Unit,
    pantry: Set<String>,
    buy: Set<String>,
    openLines: Map<String, app.necessityq.domain.ListLine>,
    ownIds: Set<String>,
    removed: List<String>,
    onToggleHome: (String, Boolean) -> Unit,
    onAdjust: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onRestore: (String) -> Unit,
    onAdd: (String) -> Unit,
) {
    val factor = servings.toDouble() / dish.servings.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("מרכיבים ל-$servings מנות", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onToggleEdit) {
                if (!editing) {
                    Icon(Icons.Default.Edit, contentDescription = null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(if (editing) "סיום" else "עריכה", fontWeight = FontWeight.SemiBold)
            }
        }
        Column(
            Modifier.fillMaxWidth().clip(NqShapes.Tile).background(MaterialTheme.colorScheme.surface)
                .border(1.dp, NqTheme.colors.hairline, NqShapes.Tile),
        ) {
            if (dish.ingredients.isEmpty()) {
                Text("אין עדיין מרכיבים. הוסיפו בעריכה.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            }
            dish.ingredients.forEachIndexed { i, text ->
                if (i > 0) androidx.compose.material3.HorizontalDivider(color = NqTheme.colors.hairline)
                val parsed = remember(text) { Recipes.parse(text) }
                val listed = parsed?.item?.let { Recipes.listItem(it, dish.listMemory?.variants.orEmpty()) }
                val name = listed?.displayName ?: text
                val shown = parsed?.quantity?.let { Recipes.scale(it, factor) }
                val amount = shown?.format() ?: "לפי הטעם"
                val id = parsed?.item?.id
                val line = listed?.id?.let { openLines[it] }
                val state = when {
                    id == null -> null
                    id in pantry || (id !in buy && parsed != null && app.necessityq.domain.Staples.assumedAtHome(parsed.item, shown)) -> IngredientState.AT_HOME
                    listed?.id in ownIds -> IngredientState.ON_LIST
                    line != null -> IngredientState.MERGE
                    else -> IngredientState.BUY
                }
                val note = listOfNotNull(
                    when (state) {
                        IngredientState.AT_HOME -> "לא ייכנס לרשימה"
                        IngredientState.ON_LIST, IngredientState.MERGE -> line?.quantity?.let { "ברשימה: ${it.format()}" }
                        else -> null
                    },
                    shown?.let { q -> parsed?.item?.name?.let { app.necessityq.domain.PieceWeights.estimate(it, q) } }
                        ?.let { app.necessityq.domain.PieceWeights.label(it) },
                ).joinToString(" · ")
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        if (note.isNotEmpty()) {
                            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (editing) {
                        FilledTonalIconButton(onClick = { onAdjust(i, 1) }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Add, contentDescription = "יותר $name", Modifier.size(18.dp))
                        }
                        Text(
                            amount,
                            Modifier.widthIn(min = 72.dp),
                            fontWeight = FontWeight.SemiBold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        IconButton(onClick = { onAdjust(i, -1) }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Remove, contentDescription = "פחות $name", Modifier.size(18.dp))
                        }
                        IconButton(onClick = { onRemove(i) }) {
                            Icon(Icons.Default.Delete, contentDescription = "הסרת $name מהמתכון", tint = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        Text(amount, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.width(8.dp))
                        if (state != null && id != null) StateBadge(state, name) { onToggleHome(id, state != IngredientState.AT_HOME) }
                    }
                }
            }
            if (editing) {
                var typed by remember { mutableStateOf("") }
                Column(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            typed, { typed = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("הוספת מרכיב") },
                            placeholder = { Text("למשל: 300 גרם פטריות") },
                            singleLine = true,
                            shape = NqShapes.Panel,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { if (typed.isNotBlank()) { onAdd(typed); typed = "" } }),
                        )
                        Button(onClick = { onAdd(typed); typed = "" }, enabled = typed.isNotBlank(), shape = NqShapes.Panel) { Text("הוספה") }
                    }
                    Text(
                        "הכמות נשמרת לפי $servings מנות, ומשתנה יחד עם שאר המרכיבים.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (removed.isNotEmpty()) {
            FlowRow(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("הוסרו:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
                removed.forEach { text ->
                    SuggestionChip(onClick = { onRestore(text) }, label = { Text("${Recipes.identity(text)?.displayName ?: text} ↺") })
                }
            }
        }
    }
}

/** What an ingredient means for the list; a tap switches "יש בבית". */
@Composable
private fun StateBadge(state: IngredientState, name: String, onClick: () -> Unit) {
    val (bg, ink) = when (state) {
        IngredientState.AT_HOME -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        IngredientState.MERGE -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        IngredientState.BUY, IngredientState.ON_LIST -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    }
    Box(
        Modifier.heightIn(min = 36.dp).widthIn(min = 84.dp).clip(NqShapes.Pill).background(bg)
            .clickable(
                role = Role.Button,
                onClickLabel = if (state == IngredientState.AT_HOME) "לקנות $name" else "יש $name בבית",
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(state.label, color = ink, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** The method as numbered steps; tapping one marks it done while cooking. */
@Composable
private fun StepsSection(steps: List<String>, done: List<Int>, onToggle: (Int) -> Unit, onWrite: () -> Unit, onImport: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (steps.isEmpty()) {
            DetailCard {
                Text("עוד אין אופן הכנה למתכון הזה.", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "מאגר צמרת נותן רק את ההרכב של המאכל. אפשר למצוא מתכון ברשת ולייבא ממנו את אופן ההכנה, או לכתוב אותו.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onImport) {
                    Icon(Icons.Default.Link, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("ייבוא אופן הכנה מהרשת")
                }
                OutlinedButton(onClick = onWrite) {
                    Icon(Icons.Default.Edit, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("כתיבת אופן הכנה")
                }
            }
            return@Column
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("אופן הכנה", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text("${done.size} / ${steps.size} בוצעו", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        steps.forEachIndexed { i, step ->
            val isDone = i in done
            Row(
                Modifier.fillMaxWidth().clip(NqShapes.Tile)
                    .background(if (isDone) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surface)
                    .border(1.dp, NqTheme.colors.hairline, NqShapes.Tile)
                    .toggleable(value = isDone, role = Role.Checkbox, onValueChange = { onToggle(i) })
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(28.dp).clip(CircleShape)
                        .background(if (isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isDone) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                    } else {
                        Text("${i + 1}", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(
                    step,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Text(
            "הקשה על שלב מסמנת שסיימתם אותו.",
            Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The floating tile that opens when a recipe is added to the list: what it will add, with a choice of kind
 * ("איזה קמח?") and pack size ("באיזה גודל אריזה?") for each ingredient. The first time everything is asked; what is
 * confirmed is remembered on the recipe ([app.necessityq.domain.ListMemory]) and the pack sizes on the household, so
 * the next time the recipe goes on the list with the same products and a message says so. Everything not at home
 * starts checked; the button adds only what is checked.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecipeAddTile(
    dish: Dish,
    servings: Int,
    lines: List<app.necessityq.domain.ListLine>,
    data: app.necessityq.domain.MealData,
    onConfirm: (chosen: Set<String>, variants: Map<String, String>, packs: Map<String, Long?>) -> Unit,
    onDismiss: () -> Unit,
) {
    val memory = dish.listMemory
    val variants = remember(dish.id) { mutableStateMapOf<String, String>().apply { putAll(memory?.variants.orEmpty()) } }
    // pack sizes changed in this tile (null = "any size"); what is not here is what the household has stored
    val packs = remember(dish.id) { mutableStateMapOf<String, Long?>() }
    val asked = dish.copy(listMemory = (memory ?: app.necessityq.domain.ListMemory()).copy(variants = variants.toMap()))
    val rows = RecipeListPreview.of(lines, asked, servings, data.pantry, data.buy)
    val chosen = remember(dish.id) {
        mutableStateListOf<String>().apply {
            addAll(
                rows.filter { r ->
                    if (memory != null && r.baseId in memory.known) r.baseId !in memory.skipped else r.kind != RecipeListPreview.Kind.AT_HOME
                }.map { it.baseId },
            )
        }
    }
    fun packFor(key: String): Long? = if (packs.containsKey(key)) packs[key] else data.packs[key]

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = NqShapes.Tile,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 24.dp).fillMaxWidth().heightIn(max = 680.dp),
        ) {
            Column(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp)) {
                    Text("מה ייכנס לרשימה", style = MaterialTheme.typography.titleLarge)
                    Text("${dish.name} · $servings מנות", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (memory == null) "בפעם הראשונה בוחרים סוג וגודל אריזה. בפעמים הבאות המתכון יתווסף עם אותם מוצרים, בלי לשאול."
                        else "אלה הבחירות מהפעם הקודמת. אפשר לשנות אותן.",
                        Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                LazyColumn(
                    Modifier.weight(1f, fill = false).fadingEdges(listState, horizontal = false),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    listOf(
                        RecipeListPreview.Kind.NEW to "חדש ברשימה",
                        RecipeListPreview.Kind.MERGE to "מתווסף לפריט שכבר ברשימה",
                        RecipeListPreview.Kind.AT_HOME to "יש בבית · לא ייכנס",
                    ).forEach { (kind, title) ->
                        val group = rows.filter { it.kind == kind }
                        if (group.isEmpty()) return@forEach
                        item(key = "h_" + kind.name) {
                            Text(
                                "$title · ${group.count { it.baseId in chosen }} / ${group.size}",
                                Modifier.padding(horizontal = 4.dp),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        item(key = "g_" + kind.name) {
                            Column(
                                Modifier.fillMaxWidth().clip(NqShapes.Panel).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                    .border(1.dp, NqTheme.colors.hairline, NqShapes.Panel),
                            ) {
                                group.forEachIndexed { i, row ->
                                    if (i > 0) androidx.compose.material3.HorizontalDivider(color = NqTheme.colors.hairline)
                                    val on = row.baseId in chosen
                                    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
                                        Row(
                                            Modifier.fillMaxWidth().heightIn(min = 52.dp)
                                                .toggleable(value = on, role = Role.Checkbox, onValueChange = { if (on) chosen.remove(row.baseId) else chosen.add(row.baseId) }),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Checkbox(checked = on, onCheckedChange = null)
                                            Spacer(Modifier.width(6.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(row.item.displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                                val detail = when (row.kind) {
                                                    RecipeListPreview.Kind.MERGE -> listOfNotNull(
                                                        row.current?.let { "ברשימה ${it.format()}" },
                                                        row.quantity?.let { "+ ${it.format()} למתכון" },
                                                    ).joinToString(" ")
                                                    RecipeListPreview.Kind.AT_HOME -> "סימנתם: יש בבית"
                                                    RecipeListPreview.Kind.NEW -> ""
                                                }
                                                if (detail.isNotEmpty()) {
                                                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                            val amount = (if (row.kind == RecipeListPreview.Kind.MERGE) row.after else row.quantity)?.format() ?: ""
                                            Text(
                                                amount,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        if (on && row.kind != RecipeListPreview.Kind.AT_HOME) {
                                            val kinds = app.necessityq.domain.ItemOptions.kindsFor(row.base)
                                            if (kinds.isNotEmpty()) {
                                                Text("איזה סוג?", Modifier.padding(start = 8.dp, top = 2.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                FlowRow(Modifier.padding(start = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    kinds.forEach { k ->
                                                        FilterChip(
                                                            selected = variants[row.baseId] == k,
                                                            onClick = { if (variants[row.baseId] == k) variants.remove(row.baseId) else variants[row.baseId] = k },
                                                            label = { Text(k) },
                                                        )
                                                    }
                                                }
                                            }
                                            val sizes = app.necessityq.domain.PackSizes.sizesFor(row.item.name, row.item.variant)
                                            val key = app.necessityq.domain.PackSizes.keyFor(row.item.name, row.item.variant)
                                            if (key != null && sizes.size > 1) {
                                                Text("באיזה גודל אריזה קונים?", Modifier.padding(start = 8.dp, top = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                FlowRow(Modifier.padding(start = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    FilterChip(selected = packFor(key) == null, onClick = { packs[key] = null }, label = { Text("הכי מתאים") })
                                                    sizes.forEach { size ->
                                                        val base = size.baseAmount.toLong()
                                                        FilterChip(selected = packFor(key) == base, onClick = { packs[key] = base }, label = { Text(size.format()) })
                                                    }
                                                }
                                            }
                                            val buy = app.necessityq.domain.PackSizes.toBuy(row.item.name, row.quantity, row.item.variant, key?.let { packFor(it) })
                                            if (buy != null && row.quantity != null && buy != row.quantity) {
                                                Text(
                                                    "לקנות ${buy.format()} · צריך ${row.quantity!!.format()}",
                                                    Modifier.padding(start = 8.dp, top = 4.dp, bottom = 2.dp),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.height(56.dp), shape = NqShapes.Field) { Text("חזרה") }
                    Button(
                        onClick = { onConfirm(chosen.toSet(), variants.toMap(), packs.toMap()) },
                        enabled = chosen.isNotEmpty(),
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = NqShapes.Field,
                    ) { Text(if (chosen.isEmpty()) "אין מה להוסיף" else "אישור והוספת ${chosen.size} פריטים") }
                }
            }
        }
    }
}

/** Create or edit a recipe: name, yield in servings, flavour, ingredients with amounts, instructions. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecipeEditScreen(vm: AppViewModel, dishId: String?, onBack: () -> Unit, onSaved: (String) -> Unit, modifier: Modifier = Modifier) {
    val data by vm.meals.collectAsStateWithLifecycle()
    val existing = dishId?.let { data.dish(it) }
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var servings by remember(existing?.id) { mutableIntStateOf(existing?.servings ?: Dish.DEFAULT_SERVINGS) }
    var flavor by remember(existing?.id) { mutableStateOf(existing?.flavor) }
    var instructions by remember(existing?.id) { mutableStateOf(existing?.instructions.orEmpty()) }
    val ingredients = remember(existing?.id) {
        mutableStateListOf<String>().apply { addAll(existing?.ingredients.orEmpty()); add("") }
    }

    fun save() {
        if (name.isBlank()) return
        val id = existing?.id ?: UUID.randomUUID().toString()
        val dish = (existing ?: Dish(id, "", emptyList())).copy(
            name = name.trim(),
            servings = servings,
            cookServings = existing?.cookServings ?: servings,
            flavor = flavor,
            instructions = instructions.trim(),
            ingredients = ingredients.map { it.trim() }.filter { it.isNotEmpty() },
        )
        vm.updateMeals { it.withDish(dish) }
        onSaved(id)
    }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.Close, contentDescription = "ביטול") } },
            title = { Text(if (existing == null) "מתכון חדש" else "עריכת מתכון", fontWeight = FontWeight.ExtraBold) },
            actions = { TextButton(enabled = name.isNotBlank(), onClick = ::save) { Text("שמירה", fontWeight = FontWeight.Bold) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        Column(
            Modifier.fadingVerticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                name, { name = it },
                label = { Text("שם המתכון") },
                singleLine = true,
                shape = NqShapes.Field,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = if (name.isNotEmpty()) { { IconButton(onClick = { name = "" }) { Icon(Icons.Default.Close, contentDescription = "מחיקת הטקסט") } } } else null,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("כמה מנות יוצאות מהמתכון?", fontWeight = FontWeight.Bold)
                    Text("הכמויות שלמטה הן לכמות הזאת", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ServingsStepper(servings, onChange = { servings = it })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("טעם:", fontWeight = FontWeight.Bold)
                Flavor.entries.forEach { f -> FilterChip(selected = flavor == f, onClick = { flavor = if (flavor == f) null else f }, label = { Text(f.label) }) }
            }

            Text("מרכיבים", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("כותבים כמו ברשימה: \"4 ביצים\", \"500 גרם פסטה\", \"בצל\".", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ingredients.forEachIndexed { i, text ->
                val parsed = remember(text) { Recipes.parse(text) }
                OutlinedTextField(
                    text,
                    { v -> ingredients[i] = v; if (i == ingredients.lastIndex && v.isNotBlank()) ingredients.add("") },
                    placeholder = { Text("מרכיב") },
                    singleLine = true,
                    shape = NqShapes.Field,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = if (parsed != null) { { Text(listOfNotNull(parsed.item.displayName, parsed.quantity?.format()).joinToString(" · ")) } } else null,
                    trailingIcon = if (text.isNotEmpty()) {
                        {
                            IconButton(onClick = { if (ingredients.size > 1) ingredients.removeAt(i) else ingredients[i] = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "מחיקת המרכיב")
                            }
                        }
                    } else null,
                )
            }

            OutlinedTextField(
                instructions, { instructions = it },
                label = { Text("אופן הכנה (לא חובה)") },
                minLines = 4,
                shape = NqShapes.Field,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = ::save, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("שמירה") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private enum class SearchScope(val label: String) { ALL("הכל"), MINE("המתכונים שלי"), TZAMERET("מאגר צמרת") }

private sealed interface RecipeHit {
    data class Mine(val dish: Dish) : RecipeHit
    data class Food(val food: NutritionFood) : RecipeHit
}

private const val PAGE = 20
private val suggestedSearches = listOf("שקשוקה", "פשטידה", "מרק", "קציצות", "אורז", "סלט", "לביבות", "עוגה")

/**
 * Full-screen recipe search over the household's recipes and Tzameret's dishes. Results refresh after a short
 * pause in typing, come in pages of [PAGE] with "הצגת עוד", and a Tzameret dish opens as a saved recipe.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecipeSearchScreen(vm: AppViewModel, onBack: () -> Unit, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val data by vm.meals.collectAsStateWithLifecycle()
    val index by vm.nutrition.collectAsStateWithLifecycle()
    val load by vm.nutritionLoad.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var debounced by remember { mutableStateOf(query.trim()) }
    var scope by remember { mutableStateOf(SearchScope.ALL) }
    var allFoods by remember { mutableStateOf(false) }
    var shown by remember { mutableIntStateOf(PAGE) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { vm.loadNutrition(); runCatching { focus.requestFocus() } }
    LaunchedEffect(query) {
        delay(300)
        debounced = query.trim()
    }
    LaunchedEffect(debounced, scope, allFoods) { shown = PAGE }

    val mine = remember(data.dishes, debounced) {
        val words = HebrewStem.words(debounced)
        if (words.isEmpty()) emptyList()
        else data.dishes.filter { d ->
            val hay = HebrewStem.words(d.name + " " + d.ingredients.joinToString(" "))
            words.all { w -> hay.any { it.startsWith(w) } }
        }.sortedBy { it.name.length }
    }
    // "dishes only" relies on Tzameret's composition table; a snapshot without it would leave the
    // filter with nothing to show, so then every food is searched and the chip is hidden
    val hasComposite = remember(index) { index?.foods?.any { it.isComposite } == true }
    val foods = remember(index, debounced, allFoods, shown, hasComposite) {
        if (debounced.length < 2) emptyList()
        else index?.search(debounced, limit = shown + 1, compositeOnly = hasComposite && !allFoods).orEmpty()
    }
    val savedCodes = data.dishes.mapNotNull { it.nutritionCode }.toSet()
    val hits: List<RecipeHit> = buildList {
        if (scope != SearchScope.TZAMERET) addAll(mine.map { RecipeHit.Mine(it) })
        if (scope != SearchScope.MINE) addAll(foods.filter { it.code !in savedCodes }.map { RecipeHit.Food(it) })
    }
    val visible = hits.take(shown)
    val typing = query.trim() != debounced
    // a result opens as a preview first; only "הוספה למתכונים שלי" saves it
    var preview by remember { mutableStateOf<NutritionFood?>(null) }
    var importing by remember { mutableStateOf(false) }
    preview?.let { food ->
        RecipePreviewSheet(vm, food, imported = null, onDismiss = { preview = null }, onSaved = { id -> preview = null; onOpen(id) })
    }
    if (importing) {
        LinkImportSheet(vm, onDismiss = { importing = false }, onSaved = { id -> importing = false; onOpen(id) })
    }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה") } },
            title = { Text("חיפוש מתכון", fontWeight = FontWeight.ExtraBold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        OutlinedTextField(
            query, { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(focus),
            placeholder = { Text("מה מבשלים? למשל שקשוקה, פשטידה…") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = if (query.isNotEmpty()) { { IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, contentDescription = "מחיקת הטקסט") } } } else null,
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        )
        FlowRow(
            Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SearchScope.entries.forEach { s -> FilterChip(selected = scope == s, onClick = { scope = s }, label = { Text(s.label) }) }
            AssistChip(
                onClick = { importing = true },
                label = { Text("ייבוא מקישור") },
                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, Modifier.size(18.dp)) },
            )
            if (scope != SearchScope.MINE && hasComposite) {
                FilterChip(selected = allFoods, onClick = { allFoods = !allFoods }, label = { Text("גם מזונות בודדים") })
            }
        }
        if (typing || load == AppViewModel.NutritionLoad.LOADING) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        } else {
            Spacer(Modifier.height(4.dp))
        }

        val searchList = androidx.compose.foundation.lazy.rememberLazyListState()
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize().fadingEdges(searchList, horizontal = false),
            state = searchList,
        ) {
            if (debounced.isEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("רעיונות לחיפוש", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            suggestedSearches.forEach { s -> SuggestionChip(onClick = { query = s }, label = { Text(s) }) }
                        }
                        if (data.dishes.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text("המתכונים שלי", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                items(data.dishes.sortedBy { it.name }, key = { "m_" + it.id }) { dish -> MineCard(dish) { onOpen(dish.id) } }
            } else {
                if (load == AppViewModel.NutritionLoad.UNAVAILABLE && scope != SearchScope.MINE) {
                    item { Text("מאגר צמרת עדיין לא זמין — מחפשים רק במתכונים שלך.", style = MaterialTheme.typography.bodySmall) }
                }
                if (!typing && visible.isEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Restaurant, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                            Text("לא נמצא \"$debounced\"", style = MaterialTheme.typography.titleMedium)
                            Text("נסו מילה אחרת, או צרו מתכון משלכם.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                items(visible, key = { h -> when (h) { is RecipeHit.Mine -> "m_" + h.dish.id; is RecipeHit.Food -> "f_" + h.food.code } }) { hit ->
                    when (hit) {
                        is RecipeHit.Mine -> MineCard(hit.dish) { onOpen(hit.dish.id) }
                        is RecipeHit.Food -> FoodCard(hit.food) { preview = hit.food }
                    }
                }
                if (hits.size > shown) {
                    item {
                        OutlinedButton(onClick = { shown += PAGE }, modifier = Modifier.fillMaxWidth()) { Text("הצגת עוד תוצאות") }
                    }
                }
                if (visible.any { it is RecipeHit.Food }) {
                    item {
                        Text(
                            "מקור: מאגר צמרת, משרד הבריאות (data.gov.il). בחירה במאכל מציגה אותו, והוא נשמר רק ב\"הוספה למתכונים שלי\".",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultCard(title: String, id: String, subtitle: String, badge: String, extra: String?, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = NqShapes.Tile,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DishTile(title, id)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        badge,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                extra?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun MineCard(dish: Dish, onClick: () -> Unit) = ResultCard(
    title = dish.name,
    id = dish.id,
    subtitle = dish.ingredients.joinToString(", ") { Recipes.identity(it)?.displayName ?: it },
    badge = "שלי",
    extra = "${dish.plannedServings} מנות",
    onClick = onClick,
)

@Composable
private fun FoodCard(food: NutritionFood, onClick: () -> Unit) = ResultCard(
    title = food.name,
    id = food.code.toString(),
    subtitle = food.parts.filter { it.percent >= 1.0 }.take(6).joinToString(", ") { it.name.substringBefore(',') },
    badge = "צמרת",
    extra = food.kcal?.let { "${app.necessityq.domain.Quantity.formatNumber(it)} קק\"ל ל-100 גרם" },
    onClick = onClick,
)
