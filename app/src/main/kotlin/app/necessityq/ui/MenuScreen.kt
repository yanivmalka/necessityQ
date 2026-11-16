package app.necessityq.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.necessityq.domain.Dish
import app.necessityq.domain.FamilyMember
import app.necessityq.domain.MealData
import app.necessityq.domain.MealPlanner
import app.necessityq.domain.MealSlot
import app.necessityq.domain.PlannedMeal
import app.necessityq.domain.Taste
import java.util.UUID

/**
 * Meal planning — first, conceptual version: family members with tastes, dishes with ingredients,
 * a weekly / bi-weekly dinner cycle with an audience per meal, "menu → list" and "bought → ideas".
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MenuScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val data by vm.meals.collectAsStateWithLifecycle()
    val purchases by vm.purchases.collectAsStateWithLifecycle()
    var week by remember { mutableIntStateOf(0) }
    var addingMember by remember { mutableStateOf(false) }
    var addingDish by remember { mutableStateOf(false) }
    var ratingDish by remember { mutableStateOf<Dish?>(null) }
    var pickingDay by remember { mutableStateOf<Int?>(null) }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            navigationIcon = { DrawerButton() },
            title = { Text("תפריט", fontWeight = FontWeight.ExtraBold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "גרסה ראשונית: בונים תפריט מהמאכלים שבני הבית אוהבים, והמרכיבים נכנסים לרשימה.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // ---- family ----
            MenuCard("בני המשפחה") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    data.members.forEach { m -> AssistChip(onClick = {}, label = { Text(m.name) }) }
                    AssistChip(onClick = { addingMember = true }, label = { Text("הוספה") }, leadingIcon = { Icon(Icons.Default.Add, null) })
                }
            }

            // ---- weekly cycle ----
            MenuCard("תפריט ערב") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(1, 2).forEachIndexed { i, weeks ->
                        SegmentedButton(
                            selected = data.plan.cycleWeeks == weeks,
                            onClick = {
                                vm.updateMeals { it.copy(plan = it.plan.copy(cycleWeeks = weeks, meals = it.plan.meals.filter { m -> m.day < weeks * 7 })) }
                                if (week >= weeks) week = 0
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                            icon = {},
                        ) { Text(if (weeks == 1) "מחזור שבועי" else "מחזור דו-שבועי") }
                    }
                }
                if (data.plan.cycleWeeks == 2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = week == 0, onClick = { week = 0 }, label = { Text("שבוע א'") })
                        FilterChip(selected = week == 1, onClick = { week = 1 }, label = { Text("שבוע ב'") })
                    }
                }
                val dishes = data.dishes.associateBy { it.id }
                MealPlanner.dayNames.forEachIndexed { i, dayName ->
                    val day = week * 7 + i
                    val meal = data.plan.meals.firstOrNull { it.day == day && it.slot == MealSlot.DINNER }
                    Row(
                        Modifier.fillMaxWidth().clickable { pickingDay = day }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(dayName, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.3f))
                        Column(Modifier.weight(0.7f)) {
                            Text(meal?.let { dishes[it.dishId]?.name } ?: "— בחירה —", style = ProductNameStyle.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize))
                            if (meal != null && meal.audience.isNotEmpty()) {
                                Text(
                                    "ל: " + data.members.filter { it.id in meal.audience }.joinToString { it.name },
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
                Button(onClick = { vm.addMenuToList(week) }, modifier = Modifier.fillMaxWidth()) { Text("הוספת המרכיבים לרשימת הקניות") }
            }

            // ---- dishes ----
            MenuCard("מאכלים") {
                if (data.dishes.isEmpty()) {
                    OutlinedButton(onClick = { vm.updateMeals { it.copy(dishes = MealPlanner.sampleDishes) } }) { Text("טעינת מאכלים לדוגמה") }
                }
                data.dishes.forEach { dish ->
                    Row(Modifier.fillMaxWidth().clickable { ratingDish = dish }.padding(vertical = 6.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(dish.name, fontWeight = FontWeight.Bold)
                            Text(dish.ingredients.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(tasteSummary(data, dish), style = MaterialTheme.typography.bodySmall)
                    }
                }
                TextButton(onClick = { addingDish = true }) { Text("+ מאכל חדש") }
            }

            // ---- reverse: from what was bought ----
            val recent = purchases.filter { System.currentTimeMillis() - it.boughtAtMillis < 14L * 86_400_000 }.map { it.itemId }.toSet()
            val ideas = MealPlanner.fromBought(data, recent).take(5)
            if (ideas.isNotEmpty()) {
                MenuCard("מה אפשר לבשל ממה שנקנה") {
                    ideas.forEach { (dish, share) -> Text("${dish.name} — יש ${(share * 100).toInt()}% מהמרכיבים") }
                }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 24.dp))
        }
    }

    if (addingMember) {
        NameDialog("בן/בת משפחה", "שם", onDismiss = { addingMember = false }) { name ->
            vm.updateMeals { it.copy(members = it.members + FamilyMember(UUID.randomUUID().toString(), name)) }
        }
    }
    if (addingDish) {
        var name by remember { mutableStateOf("") }
        var ingredients by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addingDish = false },
            title = { Text("מאכל חדש") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("שם המאכל") }, singleLine = true)
                    OutlinedTextField(ingredients, { ingredients = it }, label = { Text("מרכיבים, מופרדים בפסיק") })
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    val dish = Dish(UUID.randomUUID().toString(), name.trim(), ingredients.split(',', '،').map { it.trim() }.filter { it.isNotEmpty() })
                    vm.updateMeals { it.copy(dishes = it.dishes + dish) }
                    addingDish = false
                }) { Text("שמירה") }
            },
            dismissButton = { TextButton(onClick = { addingDish = false }) { Text("ביטול") } },
        )
    }
    ratingDish?.let { dish -> TasteDialog(vm, data, dish, onDismiss = { ratingDish = null }) }
    pickingDay?.let { day -> PickDishDialog(vm, data, day, onDismiss = { pickingDay = null }) }
}

private fun tasteSummary(data: MealData, dish: Dish): String {
    val loves = data.members.count { data.tastes[it.id]?.get(dish.id) in setOf(Taste.LOVE, Taste.LIKE) }
    val nos = data.members.count { data.tastes[it.id]?.get(dish.id) in setOf(Taste.DISLIKE, Taste.NEVER) }
    return listOfNotNull(if (loves > 0) "♥ $loves" else null, if (nos > 0) "✕ $nos" else null).joinToString("  ")
}

@Composable
private fun MenuCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = NqShapes.Tile,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun NameDialog(title: String, label: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(name, { name = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim()); onDismiss() }) { Text("שמירה") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}

/** Each family member rates a dish: אוהב מאוד … בכלל לא. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TasteDialog(vm: AppViewModel, data: MealData, dish: Dish, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dish.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (data.members.isEmpty()) Text("הוסיפו קודם בני משפחה.")
                data.members.forEach { m ->
                    Text(m.name, fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Taste.entries.forEach { t ->
                            val selected = data.tastes[m.id]?.get(dish.id) == t
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    vm.updateMeals { d ->
                                        val mine = d.tastes[m.id].orEmpty()
                                        val next = if (selected) mine - dish.id else mine + (dish.id to t)
                                        d.copy(tastes = d.tastes + (m.id to next))
                                    }
                                },
                                label = { Text(t.label) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("סגירה") } },
        dismissButton = {
            TextButton(onClick = {
                vm.updateMeals { d -> d.copy(dishes = d.dishes - dish, plan = d.plan.copy(meals = d.plan.meals.filter { it.dishId != dish.id })) }
                onDismiss()
            }) { Text("מחיקת המאכל") }
        },
    )
}

/** Choose who eats this meal, then a dish — ranked by their tastes (anyone's "בכלל לא" removes it). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PickDishDialog(vm: AppViewModel, data: MealData, day: Int, onDismiss: () -> Unit) {
    val current = data.plan.meals.firstOrNull { it.day == day && it.slot == MealSlot.DINNER }
    var audience by remember { mutableStateOf(current?.audience ?: emptySet()) }
    val ranked = MealPlanner.rank(data, audience)
    var showAll by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ערב · יום ${MealPlanner.dayNames[day % 7]}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (data.members.isNotEmpty()) {
                    Text("מי אוכל (ריק = כולם)", style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        data.members.forEach { m ->
                            FilterChip(
                                selected = m.id in audience,
                                onClick = { audience = if (m.id in audience) audience - m.id else audience + m.id },
                                label = { Text(m.name) },
                            )
                        }
                    }
                }
                Text("מאכלים מומלצים", style = MaterialTheme.typography.bodySmall)
                if (ranked.isEmpty()) Text("אין מאכלים מתאימים. הוסיפו מאכלים במסך התפריט.")
                (if (showAll) ranked else ranked.take(6)).forEach { (dish, score) ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            vm.updateMeals { d ->
                                val others = d.plan.meals.filterNot { it.day == day && it.slot == MealSlot.DINNER }
                                d.copy(plan = d.plan.copy(meals = others + PlannedMeal(day, MealSlot.DINNER, dish.id, audience)))
                            }
                            onDismiss()
                        }.padding(vertical = 6.dp),
                    ) {
                        Text(dish.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (score > 0) Text("♥ $score")
                    }
                }
                if (!showAll && ranked.size > 6) TextButton(onClick = { showAll = true }) { Text("עוד…") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("סגירה") } },
        dismissButton = {
            if (current != null) {
                TextButton(onClick = {
                    vm.updateMeals { d -> d.copy(plan = d.plan.copy(meals = d.plan.meals - current)) }
                    onDismiss()
                }) { Text("ניקוי") }
            }
        },
    )
}
