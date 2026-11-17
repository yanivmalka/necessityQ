package app.necessityq.ui

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import app.necessityq.domain.QuickEntryParser
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
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            title?.let { Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            content()
        }
    }
}

/**
 * A recipe in full: servings (the quantities follow), its ingredients, instructions, the family's stars,
 * where it is on the menu, and putting it on the shopping list — or updating the list when the servings changed.
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
            title = { Text("מתכון") },
            actions = {
                if (dish != null) {
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "עריכת המתכון") }
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
        val lines = snapshot?.lines.orEmpty()
        val onList = RecipeListSync.onList(lines, dish)
        val neededIds = lines.filter { it.status == LineStatus.NEEDED }.map { it.itemId }.toSet()

        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---- header ----
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                DishTile(dish.name, dish.id, size = 64)
                Column(Modifier.weight(1f)) {
                    Text(dish.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    val meta = listOfNotNull(
                        dish.flavor?.label,
                        "${dish.ingredients.size} מרכיבים",
                        if (dish.nutritionCode != null) "ממאגר צמרת" else null,
                    ).joinToString(" · ")
                    Text(meta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            dish.nutritionCode?.let { nutrition?.food(it) }?.summary()?.takeIf { it.isNotEmpty() }?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // ---- servings + list ----
            DetailCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("לכמה מנות?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("המתכון המקורי: ${dish.servings} מנות", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    ServingsStepper(servings, onChange = { n ->
                        servings = n
                        vm.updateMeals { d -> d.dish(dish.id)?.let { d.withDish(it.copy(cookServings = n)) } ?: d }
                    })
                }
                val listServings = dish.listServings
                when {
                    onList.isNotEmpty() && listServings != null && listServings != servings -> {
                        Text("ברשימת הקניות לפי $listServings מנות.", style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { vm.addRecipeToList(dish.id, servings) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.ShoppingCart, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                            Text("עדכון הכמויות ברשימה ל-$servings מנות")
                        }
                    }
                    onList.isNotEmpty() -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("${onList.size} מרכיבים ברשימת הקניות (${listServings ?: servings} מנות)", style = MaterialTheme.typography.bodyMedium)
                    }
                    else -> Button(onClick = { vm.addRecipeToList(dish.id, servings) }, modifier = Modifier.fillMaxWidth(), enabled = dish.ingredients.isNotEmpty()) {
                        Icon(Icons.Default.ShoppingCart, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                        Text("הוספת המרכיבים לרשימה")
                    }
                }
            }

            // ---- ingredients ----
            DetailCard("מרכיבים · $servings מנות") {
                if (dish.ingredients.isEmpty()) Text("אין מרכיבים. הוסיפו בעריכת המתכון.")
                dish.ingredients.forEach { text ->
                    val id = Recipes.identity(text)?.id
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                        Spacer(Modifier.width(10.dp))
                        Text(Recipes.display(dish, text, servings), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (id != null && id in neededIds) {
                            Icon(Icons.Default.ShoppingCart, contentDescription = "ברשימה", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            if (dish.instructions.isNotBlank()) {
                DetailCard("אופן הכנה") { Text(dish.instructions, style = MaterialTheme.typography.bodyLarge) }
            }

            // ---- family stars ----
            if (data.members.isNotEmpty()) {
                DetailCard("מה המשפחה חושבת") {
                    data.members.forEach { m ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MemberAvatar(m, size = 36.dp)
                            Column {
                                Text(m.name + if (m.linkedUserId == vm.me) " (אני)" else "", fontWeight = FontWeight.Bold)
                                StarsRow(data.rating(m.id, dish.id), onChange = { stars -> vm.updateMeals { it.withRating(m.id, dish.id, stars) } }, starSize = 20.dp)
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
            Spacer(Modifier.height(24.dp))
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
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
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
                val parsed = remember(text) { QuickEntryParser.parse(text) }
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
    val foods = remember(index, debounced, allFoods, shown) {
        if (debounced.length < 2) emptyList() else index?.search(debounced, limit = shown + 1, compositeOnly = !allFoods).orEmpty()
    }
    val savedCodes = data.dishes.mapNotNull { it.nutritionCode }.toSet()
    val hits: List<RecipeHit> = buildList {
        if (scope != SearchScope.TZAMERET) addAll(mine.map { RecipeHit.Mine(it) })
        if (scope != SearchScope.MINE) addAll(foods.filter { it.code !in savedCodes }.map { RecipeHit.Food(it) })
    }
    val visible = hits.take(shown)
    val typing = query.trim() != debounced

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
            if (scope != SearchScope.MINE) {
                FilterChip(selected = allFoods, onClick = { allFoods = !allFoods }, label = { Text("גם מזונות בודדים") })
            }
        }
        if (typing || load == AppViewModel.NutritionLoad.LOADING) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        } else {
            Spacer(Modifier.height(4.dp))
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
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
                        is RecipeHit.Food -> FoodCard(hit.food) { vm.addDishFromNutrition(hit.food)?.let(onOpen) }
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
                            "מקור: מאגר צמרת, משרד הבריאות (data.gov.il). בחירה במאכל שומרת אותו במתכונים שלך.",
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
