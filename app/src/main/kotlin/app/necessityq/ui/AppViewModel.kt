package app.necessityq.ui

import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.necessityq.AppContainer
import app.necessityq.domain.Autocomplete
import app.necessityq.domain.HebrewText
import app.necessityq.domain.ItemIdentity
import app.necessityq.domain.LineSource
import app.necessityq.domain.ListLine
import app.necessityq.domain.ListSnapshot
import app.necessityq.domain.ItemMessage
import app.necessityq.domain.ListMode
import app.necessityq.domain.MealData
import app.necessityq.domain.MealPlanner
import app.necessityq.domain.PickOrder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
import app.necessityq.domain.LineStatus
import app.necessityq.domain.ListSort
import app.necessityq.domain.Member
import app.necessityq.domain.NotifySettings
import app.necessityq.domain.Store
import app.necessityq.domain.StoreType
import app.necessityq.domain.Stores
import app.necessityq.platform.DisplayOptions
import app.necessityq.platform.LocationHelper
import app.necessityq.domain.Purchase
import app.necessityq.domain.Releases
import app.necessityq.BuildConfig
import java.io.File
import app.necessityq.domain.Quantity
import app.necessityq.domain.QuickEntryParser
import app.necessityq.domain.Session
import app.necessityq.domain.Suggestion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Single screen-state holder. All data comes from realtime repository streams, so a change
 * made by any household member flows through [snapshot] and recomposes the UI — there is no
 * manual refresh and no local copy to get out of sync.
 */
@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
class AppViewModel(private val container: AppContainer) : ViewModel() {

    val isShared: Boolean = container.household.isShared

    // a burst of messages is never dropped: each one is shown (in layers) until it goes
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val messages: SharedFlow<String> = _messages

    val session: StateFlow<Session> = container.household.session
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Session.Loading)

    private val householdId = session.map { (it as? Session.SignedIn)?.household?.id }

    val snapshot: StateFlow<ListSnapshot?> = householdId
        .flatMapLatest { hid -> if (hid == null) emptyFlow() else container.service.snapshot(hid) }
        .catch { _messages.tryEmit("שגיאה בסנכרון: ${it.message}") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val purchases: StateFlow<List<Purchase>> = householdId
        .flatMapLatest { hid -> if (hid == null) emptyFlow() else container.service.purchases(hid) }
        .map { list -> list.sortedByDescending { it.boughtAtMillis } }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Quick-add text box. */
    val query = MutableStateFlow("")

    /**
     * [query] once typing pauses ([SEARCH_DELAY_MS]); cleared at once. The searches (list filter, completions,
     * branch products) run on this, so typing or deleting letter by letter does not search on every letter.
     */
    val searchQuery: StateFlow<String> = query
        .debounce { if (it.isBlank()) 0L else SEARCH_DELAY_MS }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val completions: StateFlow<List<ItemIdentity>> = combine(searchQuery, snapshot) { q, snap -> q to snap }
        .mapLatest { (q, snap) ->
            val name = QuickEntryParser.parse(q)?.item?.displayName ?: q
            if (q.isBlank()) emptyList() else Autocomplete.complete(name, snap?.stats?.values.orEmpty())
        }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun ctx(): Triple<String, String, List<ListLine>>? {
        val s = session.value as? Session.SignedIn ?: return null
        val hid = s.household?.id ?: return null
        return Triple(hid, s.userId, snapshot.value?.lines.orEmpty())
    }

    /** Shows a short message (snackbar). */
    fun showMessage(text: String) { _messages.tryEmit(text) }

    private fun launchSafe(block: suspend () -> Unit) = viewModelScope.launch {
        runCatching { block() }.onFailure { _messages.tryEmit(it.message ?: "פעולה נכשלה") }
    }

    private val _added = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** Item ids just added to the list: the list scrolls to them. */
    val added: SharedFlow<String> = _added

    fun submitQuery() {
        val text = query.value
        val (hid, uid, lines) = ctx() ?: return
        if (text.isBlank()) return
        query.value = ""
        launchSafe { container.service.addFromText(hid, uid, text, lines)?.let { _added.tryEmit(it.item.id) } }
    }


    /** Picks an autocomplete entry, keeping any quantity typed in the box ("2 קוט…" → 2 × קוטג'). */
    fun pickCompletion(item: ItemIdentity) {
        val (hid, uid, lines) = ctx() ?: return
        val quantity = QuickEntryParser.parse(query.value)?.quantity
        query.value = ""
        launchSafe { container.service.add(hid, uid, item, quantity, LineSource.MANUAL, lines); _added.tryEmit(item.id) }
    }

    fun accept(suggestion: Suggestion) {
        val (hid, uid, lines) = ctx() ?: return
        launchSafe { container.service.acceptSuggestion(hid, uid, suggestion, lines); _added.tryEmit(suggestion.itemId) }
    }

    fun dismiss(suggestion: Suggestion) {
        val (hid, _, _) = ctx() ?: return
        launchSafe { container.service.dismissSuggestion(hid, suggestion, snapshot.value?.stats?.get(suggestion.itemId)) }
    }

    fun checkOff(line: ListLine) {
        val (hid, uid, _) = ctx() ?: return
        launchSafe { container.service.checkOff(hid, uid, line, currentStoreId.value) }
        _undo.tryEmit(UndoEvent("${line.displayName} הושלם") { reopen(line) })
    }

    fun reopen(line: ListLine) {
        val (hid, _, _) = ctx() ?: return
        launchSafe { container.service.reopen(hid, line) }
    }

    /**
     * Changes the kind of quantity (יח' / מארז / ק"ג …, or none). Converts within a dimension; otherwise
     * takes the household's usual amount in that unit (15 מלפפונים → ק"ג uses what is usually bought).
     */
    fun setQuantityUnit(line: ListLine, unit: app.necessityq.domain.QuantityUnit?) {
        val usual = snapshot.value?.stats?.get(line.itemId)?.typicalQuantity
        val next = app.necessityq.domain.QuantitySteps.switchUnit(line.quantity, unit, usual)
        if (next != line.quantity) changeQuantity(line, next)
    }

    /** [approved]: the person confirmed an amount that is more than usual for the item (no "כמות חריגה" tag). */
    fun changeQuantity(line: ListLine, quantity: Quantity?, approved: Boolean = false) {
        val (hid, _, _) = ctx() ?: return
        launchSafe {
            if (approved && quantity != null) container.service.changeQuantityApproved(hid, line, quantity)
            else container.service.changeQuantity(hid, line, quantity)
        }
    }

    /** The household's usual amount widens what counts as usual for the item. */
    fun quantityCheck(line: ListLine, quantity: Quantity?): app.necessityq.domain.QuantityCheck? =
        app.necessityq.domain.QuantityNorms.check(line.name, line.category, quantity, snapshot.value?.stats?.get(line.itemId)?.typicalQuantity)

    /**
     * Renames the product as the user wrote it. The exact product the household chose for it (by
     * scan or from the branch list) and an entered price move with it, unless the new name has its own.
     */
    fun renameLine(line: ListLine, text: String) {
        val (hid, _, _) = ctx() ?: return
        launchSafe {
            val renamed = container.service.rename(hid, line, text) ?: return@launchSafe
            if (renamed.id == line.itemId) return@launchSafe
            val chosen = preferredProducts.value[line.itemId]
            if (chosen != null && preferredProducts.value[renamed.id] == null) container.prices.setPreferredProduct(hid, renamed.id, chosen)
            val price = prices.value[line.itemId]
            if (price != null && prices.value[renamed.id] == null) container.collab.setPrice(hid, renamed.id, price)
        }
    }

    fun changeNote(line: ListLine, note: String?) {
        val (hid, _, _) = ctx() ?: return
        launchSafe { container.service.changeNote(hid, line, note) }
    }

    fun delete(line: ListLine) {
        val (hid, _, _) = ctx() ?: return
        launchSafe { container.service.delete(hid, line) }
        _undo.tryEmit(UndoEvent("${line.displayName} הוסר") { restore(line, line.status) })
    }

    /** [text]: the message shown with "ביטול" (by default, which items went to the recycle bin). */
    fun clearBought(text: String? = null) {
        val (hid, _, lines) = ctx() ?: return
        val bought = lines.filter { it.status == LineStatus.BOUGHT }
        launchSafe { container.service.clearBought(hid, lines) }
        if (bought.isNotEmpty()) {
            val undoChats = archiveChats(bought.map { it.itemId }, lines.filter { it.status == LineStatus.NEEDED })
            _undo.tryEmit(
                UndoEvent(text ?: if (bought.size == 1) "${bought[0].displayName} עבר לסל המחזור" else "${HebrewText.nameList(bought.map { it.displayName })} עברו לסל המחזור") {
                    bought.forEach { restore(it, LineStatus.BOUGHT) }
                    undoChats?.invoke()
                },
            )
        }
    }

    // ---------- list from past purchases, reset ----------

    fun proposeList(mode: app.necessityq.domain.GenerateMode): List<app.necessityq.domain.GeneratedItem> {
        val snap = snapshot.value ?: return emptyList()
        return app.necessityq.domain.ListGenerator.propose(
            mode, snap.stats.values, purchases.value, snap.lines, java.time.LocalDate.now(),
        )
    }

    fun addGenerated(items: List<app.necessityq.domain.GeneratedItem>) {
        val (hid, uid, lines) = ctx() ?: return
        if (items.isEmpty()) return
        launchSafe {
            val onList = lines.filter { it.status == LineStatus.NEEDED }.map { it.itemId }.toSet()
            val names = items.filter { it.identity.id !in onList }.map { it.identity.displayName }.distinct()
            val added = container.service.addGenerated(hid, uid, items, lines)
            if (added.isEmpty()) return@launchSafe
            _undo.tryEmit(UndoEvent("נוספו מקניות עבר: " + HebrewText.nameList(names.ifEmpty { listOf("${added.size} פריטים") })) { launchSafe { container.service.undoGenerated(hid, added) } })
        }
    }

    fun resetList(scope: app.necessityq.domain.ResetScope) {
        val (hid, _, lines) = ctx() ?: return
        launchSafe {
            val moved = container.service.resetList(hid, scope, lines)
            if (moved.isEmpty()) {
                _messages.tryEmit("אין מה לנקות")
            } else {
                val movedIds = moved.map { it.id }.toSet()
                val undoChats = archiveChats(moved.map { it.itemId }, lines.filter { it.id !in movedIds })
                _undo.tryEmit(
                    UndoEvent(if (moved.size == 1) "${moved[0].displayName} הוסר" else "${HebrewText.nameList(moved.map { it.displayName })} הוסרו") {
                        moved.forEach { restore(it, it.status) }
                        undoChats?.invoke()
                    },
                )
            }
        }
    }

    fun deletePurchase(purchase: Purchase) {
        val (hid, _, _) = ctx() ?: return
        launchSafe { container.service.deletePurchase(hid, purchase) }
    }

    fun importSample() {
        val (hid, uid, lines) = ctx() ?: return
        launchSafe {
            val (added, history) = container.service.importTodoSample(hid, uid, lines)
            _messages.tryEmit("נטענו $history רשומות היסטוריה ו-$added פריטים לרשימה")
        }
    }

    // ---------- display, sorting, stores, favourites ----------

    private val prefs get() = container.prefs

    val brightTheme = MutableStateFlow(prefs.brightTheme)
    val mode = MutableStateFlow(prefs.mode)
    val sort = MutableStateFlow(prefs.sortFor(prefs.mode))
    val display = MutableStateFlow(prefs.display)
    val currentStoreId = MutableStateFlow(prefs.currentStoreId)

    fun setBrightTheme(on: Boolean) { prefs.brightTheme = on; brightTheme.value = on }
    val palette = MutableStateFlow(AppPalette.of(prefs.palette))
    fun setPalette(value: AppPalette) { prefs.palette = value.name; palette.value = value }
    fun setSort(value: ListSort) { prefs.setSortFor(mode.value, value); sort.value = value }
    fun setMode(value: ListMode) {
        prefs.mode = value
        mode.value = value
        sort.value = prefs.sortFor(value)
        descending.value = prefs.descendingFor(value)
        checkStorePrompt()
        // back to preparing (the arrow or the system back) keeps the trip and its split: only "סיום קנייה" ends it
        if (value == ListMode.SHOP) {
            refreshStalePricesForShopping()
            locate()
        }
    }

    /** Sort direction (per stage of use). */
    val descending = MutableStateFlow(prefs.descendingFor(prefs.mode))
    fun setDescending(value: Boolean) { prefs.setDescendingFor(mode.value, value); descending.value = value }

    /** Filter of the open lines (not persisted: a filter is for the moment). */
    val filter = MutableStateFlow(app.necessityq.domain.ListFilter())
    fun setFilter(value: app.necessityq.domain.ListFilter) { filter.value = value }

    /** "Where are we shopping?" — asked once at the start of a shopping trip; optional, editable later. */
    val storePrompt = MutableStateFlow(false)

    private fun checkStorePrompt() {
        val newTrip = System.currentTimeMillis() - prefs.storeAskedAt > 4L * 60 * 60 * 1000
        storePrompt.value = mode.value == ListMode.SHOP && newTrip
    }

    fun answerStorePrompt(storeId: String?, skipped: Boolean = false) {
        if (!skipped) selectStore(storeId)
        prefs.storeAskedAt = System.currentTimeMillis()
        storePrompt.value = false
    }

    val collapsed = MutableStateFlow(prefs.collapsed)
    fun toggleCollapsed(key: String) {
        val next = if (key in collapsed.value) collapsed.value - key else collapsed.value + key
        prefs.collapsed = next
        collapsed.value = next
    }

    /** Collapse every given section, or (null) expand them all. */
    fun setAllCollapsed(keys: Set<String>?) {
        val next = keys ?: emptySet()
        prefs.collapsed = next
        collapsed.value = next
    }

    /** Learned collection order for the current store (used by the "pick order" sort). */
    val pickRanks: StateFlow<Map<String, Double>> = combine(purchases, currentStoreId) { list, store ->
        PickOrder.ranks(list, store)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    // ---------- undo ----------

    /**
     * A reversible action just done; its message has its own "ביטול" for half a minute. Also a message with another
     * one-tap action ([label]), such as "עדכון" on a new version.
     */
    class UndoEvent(val text: String, val label: String = "ביטול", val undo: () -> Unit)
    // every undo is kept (the old queue of four dropped the rest)
    private val _undo = MutableSharedFlow<UndoEvent>(extraBufferCapacity = 64)
    val undo: SharedFlow<UndoEvent> = _undo

    fun restore(line: ListLine, status: LineStatus = line.removedFrom?.takeIf { it != LineStatus.REMOVED } ?: LineStatus.NEEDED) {
        val (hid, _, _) = ctx() ?: return
        launchSafe { container.service.restore(hid, line, status) }
    }

    fun restoreToList(line: ListLine) = restore(line, LineStatus.NEEDED)

    fun emptyBin() {
        val (hid, _, lines) = ctx() ?: return
        launchSafe { container.service.purge(hid, lines) }
    }

    // ---------- meal planning (conceptual) ----------

    /** The household's meal data, null until it has been read (so nothing is written over it before then). */
    private val loadedMeals: StateFlow<MealData?> = householdId
        .flatMapLatest<String?, MealData?> { hid -> if (hid == null) emptyFlow() else container.meals.meals(hid) }
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val meals: StateFlow<MealData> = loadedMeals
        .map { it ?: MealData() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MealData())

    fun updateMeals(change: (MealData) -> MealData) {
        val (hid, _, _) = ctx() ?: return
        val next = MealPlanner.prune(change(meals.value), java.time.LocalDate.now())
        launchSafe { container.meals.save(hid, next) }
    }

    // ---------- Tzameret (national nutrition database) ----------

    enum class NutritionLoad { IDLE, LOADING, READY, UNAVAILABLE }

    private val _nutrition = MutableStateFlow<app.necessityq.domain.NutritionIndex?>(null)
    val nutrition: StateFlow<app.necessityq.domain.NutritionIndex?> = _nutrition
    private val _nutritionLoad = MutableStateFlow(NutritionLoad.IDLE)
    val nutritionLoad: StateFlow<NutritionLoad> = _nutritionLoad

    /** Loads the Tzameret snapshot (from the cache when it is current). Called when the menu needs it. */
    fun loadNutrition() {
        if (_nutritionLoad.value == NutritionLoad.LOADING || _nutritionLoad.value == NutritionLoad.READY) return
        _nutritionLoad.value = NutritionLoad.LOADING
        viewModelScope.launch {
            val index = runCatching { container.nutrition.index() }.getOrNull()
            _nutrition.value = index
            _nutritionLoad.value = if (index != null && index.foods.isNotEmpty()) NutritionLoad.READY else NutritionLoad.UNAVAILABLE
        }
    }

    /**
     * Adds a dish built from a Tzameret food (its composition becomes the ingredient list) and returns its id;
     * when that food is already one of the household's recipes, the existing recipe's id.
     */
    fun addDishFromNutrition(food: app.necessityq.domain.NutritionFood, imported: app.necessityq.domain.ImportedRecipe? = null): String? {
        meals.value.dishes.firstOrNull { it.nutritionCode == food.code }?.let { return it.id }
        val index = _nutrition.value ?: return null
        val base = index.dishFrom(food, java.util.UUID.randomUUID().toString())
        // a method found on the web comes with that recipe's own ingredients and yield
        val dish = if (imported == null) base else base.copy(
            ingredients = imported.ingredients.ifEmpty { base.ingredients },
            instructions = imported.instructions,
            servings = imported.servings ?: base.servings,
        )
        updateMeals { it.withDish(dish) }
        _messages.tryEmit("נשמר במתכונים: ${dish.name}")
        return dish.id
    }

    /** Reads the recipe on a web page (for a preview; nothing is saved). */
    suspend fun fetchRecipe(url: String): app.necessityq.domain.ImportedRecipe = app.necessityq.platform.RecipeFetcher.fetch(url)

    /** Saves a recipe read from a web page as one of the household's recipes and returns its id. */
    fun addImportedRecipe(recipe: app.necessityq.domain.ImportedRecipe): String {
        val servings = recipe.servings ?: app.necessityq.domain.Dish.DEFAULT_SERVINGS
        val dish = app.necessityq.domain.Dish(
            id = java.util.UUID.randomUUID().toString(),
            name = recipe.name.ifBlank { "מתכון" },
            ingredients = recipe.ingredients,
            servings = servings,
            cookServings = servings,
            instructions = recipe.instructions,
        )
        updateMeals { it.withDish(dish) }
        _messages.tryEmit("נשמר במתכונים: ${dish.name}")
        return dish.id
    }

    /** Adds the ingredients of the meals in the next [days] days (from today) to the list, scaled to each meal's servings. */
    fun addMenuToList(fromEpochDay: Long, days: Int) {
        val (hid, uid, _) = ctx() ?: return
        val items = MealPlanner.ingredientsFor(meals.value, fromEpochDay, days)
        if (items.isEmpty()) { _messages.tryEmit("אין ארוחות מתוכננות בימים האלה"); return }
        launchSafe {
            // one line per ingredient with enough for every meal that needs it; pressing again changes nothing
            var lines = container.service.snapshot(hid).first().lines
            val changes = app.necessityq.domain.MenuListSync.plan(lines, items)
            var added = 0
            var updated = 0
            changes.forEach { change ->
                when (change) {
                    is app.necessityq.domain.MenuListSync.Change.Add -> {
                        container.service.add(hid, uid, change.item, change.quantity, LineSource.MENU, lines, recipe = change.parts.first().dish)
                        lines = container.service.snapshot(hid).first().lines
                        if (change.parts.size > 1) {
                            lines.firstOrNull { it.itemId == change.item.id && it.status == app.necessityq.domain.LineStatus.NEEDED }
                                ?.let { container.service.setRecipeParts(hid, it, change.parts) }
                        }
                        added++
                    }
                    is app.necessityq.domain.MenuListSync.Change.Update -> {
                        val line = lines.firstOrNull { it.id == change.lineId } ?: return@forEach
                        if (change.quantity != line.quantity) container.service.changeQuantity(hid, line, change.quantity)
                        container.service.setRecipeParts(hid, line, change.parts)
                        updated++
                    }
                }
                lines = container.service.snapshot(hid).first().lines
            }
            _messages.tryEmit(
                when {
                    changes.isEmpty() -> "הרשימה כבר מספיקה לכל הארוחות בתפריט"
                    updated == 0 -> "נוספו $added מרכיבים מהתפריט לרשימה"
                    added == 0 -> "עודכנו $updated מרכיבים ברשימה כך שיספיקו לכל הארוחות"
                    else -> "נוספו $added מרכיבים ועודכנו $updated, כך שיספיקו לכל הארוחות"
                },
            )
        }
    }

    /**
     * Puts a recipe's ingredients on the list for [servings]. When the recipe is already on the list,
     * the quantities there are changed by the difference instead of adding everything again.
     *
     * The first time, the recipe screen asks which kind of each ingredient and which pack size, and passes the answer:
     * [only] the ingredients to add (base ids), [variants] the kind chosen per ingredient, [packs] the pack size
     * per item (null = any). That is remembered on the dish ([ListMemory]); from then on the recipe goes on the
     * list with the same products and a message says so. What was left out is also remembered, so changing
     * servings later does not add it.
     */
    fun addRecipeToList(
        dishId: String,
        servings: Int,
        only: Set<String>? = null,
        variants: Map<String, String>? = null,
        packs: Map<String, Long?>? = null,
    ) {
        val (hid, uid, _) = ctx() ?: return
        val data = meals.value
        val stored = data.dish(dishId) ?: return
        val confirming = variants != null
        // the dish as it is added: with the kinds just chosen
        val dish = if (confirming) stored.copy(listMemory = (stored.listMemory ?: app.necessityq.domain.ListMemory()).copy(variants = variants.orEmpty())) else stored
        val lines = snapshot.value?.lines.orEmpty()
        val onList = app.necessityq.domain.RecipeListSync.onList(lines, dish).isNotEmpty()
        val from = dish.listServings?.takeIf { onList }
        launchSafe {
            var current = lines
            var skip = dish.listSkip
            var memory = dish.listMemory
            if (from == null) {
                val all = app.necessityq.domain.Recipes.scaled(dish, servings)
                val chosen = all.filter { if (only != null) it.baseId in only else !data.leavesOut(dish, it) }
                chosen.forEach { ing ->
                    container.service.add(hid, uid, ing.item, ing.quantity, LineSource.RECIPE, current, recipe = dish.name)
                    current = container.service.snapshot(hid).first().lines
                }
                skip = all.map { it.baseId }.toSet() - chosen.map { it.baseId }.toSet()
                if (confirming) memory = app.necessityq.domain.ListMemory(variants.orEmpty(), all.map { it.baseId }.toSet(), skip)
                _messages.tryEmit(
                    if (!confirming && dish.listMemory != null) "${dish.name}: נוספו אותם מוצרים כמו בפעם הקודמת (${chosen.size} מרכיבים ל-$servings מנות)"
                    else "${dish.name}: ${chosen.size} מרכיבים ל-$servings מנות נוספו לרשימה",
                )
            } else {
                app.necessityq.domain.RecipeListSync.plan(current, dish, from, servings, skip = data.pantry + dish.listSkip).forEach { change ->
                    when (change) {
                        is app.necessityq.domain.RecipeListSync.Change.Update -> {
                            val line = current.firstOrNull { it.id == change.lineId } ?: return@forEach
                            container.service.changeQuantity(hid, line, change.quantity)
                            container.service.setRecipePart(hid, line, app.necessityq.domain.RecipePart(dish.name, change.recipeQuantity))
                        }
                        is app.necessityq.domain.RecipeListSync.Change.Add ->
                            container.service.add(hid, uid, change.ingredient.item, change.ingredient.quantity, LineSource.RECIPE, current, recipe = dish.name)
                    }
                    current = container.service.snapshot(hid).first().lines
                }
                _messages.tryEmit("${dish.name}: הכמויות ברשימה עודכנו ל-$servings מנות")
            }
            updateMeals { d ->
                val withPacks = packs?.entries?.fold(d) { acc, (key, size) -> acc.withPack(key, size) } ?: d
                withPacks.dish(dishId)?.let { withPacks.withDish(it.copy(listServings = servings, cookServings = servings, listSkip = skip, listMemory = memory)) } ?: withPacks
            }
        }
    }

    /**
     * Takes a recipe off the list: lines that only served it go to the recycle bin, shared lines shrink by the
     * dish's amount. Undo puts it back for the same servings.
     */
    fun removeRecipeFromList(dishId: String) {
        val (hid, _, lines) = ctx() ?: return
        val dish = meals.value.dish(dishId) ?: return
        val servings = dish.listServings ?: dish.plannedServings
        val skip = dish.listSkip
        launchSafe {
            app.necessityq.domain.RecipeListSync.removal(lines, dish).forEach { change ->
                when (change) {
                    is app.necessityq.domain.RecipeListSync.Removal.Delete ->
                        lines.firstOrNull { it.id == change.lineId }?.let { container.service.delete(hid, it) }
                    is app.necessityq.domain.RecipeListSync.Removal.Shrink -> {
                        val line = lines.firstOrNull { it.id == change.lineId } ?: return@forEach
                        if (change.quantity != line.quantity) container.service.changeQuantity(hid, line, change.quantity)
                        container.service.setRecipeParts(hid, line, change.parts)
                    }
                }
            }
            updateMeals { d -> d.dish(dishId)?.let { d.withDish(it.copy(listServings = null, listSkip = emptySet())) } ?: d }
            _undo.tryEmit(
                UndoEvent("${dish.name} הוסר מהרשימה") {
                    val all = app.necessityq.domain.Recipes.scaled(dish, servings).map { it.baseId }.toSet()
                    addRecipeToList(dishId, servings, only = all - skip)
                },
            )
        }
    }

    /** Marks an ingredient as always at home (recipes and the menu leave it off the list), or not. */
    fun setAtHome(itemId: String, atHome: Boolean) = updateMeals { it.withPantry(itemId, atHome) }

    /** Changes one recipe (its ingredients, servings…) in place. */
    fun editDish(dishId: String, change: (app.necessityq.domain.Dish) -> app.necessityq.domain.Dish) =
        updateMeals { d -> d.dish(dishId)?.let { d.withDish(change(it)) } ?: d }

    fun setDisplay(value: DisplayOptions) { prefs.display = value; display.value = value }

    /** The order of the tabs and the one the app opens on ("הגדרות" ← "סדר הלשוניות"). */
    val tabOrder = MutableStateFlow(prefs.tabOrder)
    val startTab = MutableStateFlow(prefs.startTab)
    fun moveTab(from: Int, to: Int) {
        val next = app.necessityq.domain.TabOrder.move(tabOrder.value, from, to)
        prefs.tabOrder = next
        tabOrder.value = next
    }
    fun setStartTab(tab: app.necessityq.domain.AppTab) { prefs.startTab = tab; startTab.value = tab }

    /** Which features are on; the first run asks a few questions about the household's habits ([finishFirstRun]). */
    val features = MutableStateFlow(prefs.features)
    val featuresChosen = MutableStateFlow(prefs.featuresChosen)
    fun setFeatures(value: app.necessityq.domain.Features) {
        val now = System.currentTimeMillis()
        // a feature just turned on is not offered to be hidden before it had its month
        app.necessityq.domain.Feature.entries.filter { it in value && it !in features.value }.forEach { prefs.markEnabled(it, now) }
        prefs.features = value
        features.value = value
        featuresChosen.value = true
        refreshAdvice()
    }

    /** The first-run questions answered: their features, and — for a shared household — straight on to inviting. */
    fun finishFirstRun(value: app.necessityq.domain.Features, invite: Boolean) {
        endFirstRunReplay(restore = false)
        setFeatures(value)
        if (invite && isShared) openInvite.value = true
    }

    /** Set after the first-run questions when the household shops together: the household page opens, to invite. */
    val openInvite = MutableStateFlow(false)
    fun consumeOpenInvite() { openInvite.value = false }

    // ---------- "הדגמת התחלה": the first run again, as a new user sees it ----------

    enum class FirstRunReplay { ONBOARDING, QUESTIONS }

    val firstRunReplay = MutableStateFlow(prefs.firstRunReplay?.let { n -> FirstRunReplay.entries.firstOrNull { it.name == n } })

    /** Bumped when the replay starts: the opening screen is shown again. */
    val introRun = MutableStateFlow(0)

    /** Only the accounts in [app.necessityq.domain.FirstRunDemo] may replay the first run. */
    fun canReplayFirstRun(account: app.necessityq.domain.Account): Boolean = app.necessityq.domain.FirstRunDemo.allowed(account.email)

    /**
     * Starts the replay: the opening screen, the welcome page (nothing is created there) and the questions, with
     * the hints and suggestions as on a new install. The list, the household and the history are not touched.
     */
    fun startFirstRunReplay() {
        if (firstRunReplay.value == null) prefs.replayBackup = features.value.encodeOff() + "\n" + features.value.encodeShopOff()
        prefs.firstRunReplay = FirstRunReplay.ONBOARDING.name
        prefs.swipeHintSeen = 0
        prefs.advisor = app.necessityq.domain.AdvisorState(since = System.currentTimeMillis())
        prefs.clearUsage()
        firstRunReplay.value = FirstRunReplay.ONBOARDING
        featuresChosen.value = false
        introRun.value = introRun.value + 1
        refreshAdvice()
    }

    /** The welcome page of the replay was passed ("יצירת בית" / "הצטרפות" do nothing there): on to the questions. */
    fun replayOnboardingDone() {
        prefs.firstRunReplay = FirstRunReplay.QUESTIONS.name
        firstRunReplay.value = FirstRunReplay.QUESTIONS
    }

    /** Leaves the replay; [restore] = left early, so the features go back to what they were. */
    fun endFirstRunReplay(restore: Boolean) {
        if (firstRunReplay.value == null) return
        val backup = prefs.replayBackup
        prefs.firstRunReplay = null
        prefs.replayBackup = null
        firstRunReplay.value = null
        if (restore && backup != null) {
            val parts = backup.split('\n')
            val before = app.necessityq.domain.Features.decodeOff(parts[0], parts.getOrNull(1))
            prefs.features = before
            features.value = before
        }
        featuresChosen.value = prefs.featuresChosen
    }

    // ---------- suggestions: features that would probably help, from how the app is used ----------

    private val _advice = MutableStateFlow<app.necessityq.domain.FeatureSuggestion?>(null)
    /** The one suggestion card to show above the list (at most one, and a new one only a week after the last answer). */
    val advice: StateFlow<app.necessityq.domain.FeatureSuggestion?> = _advice

    private val _declinedAdvice = MutableStateFlow(prefs.advisor.declined)
    /** Suggestions answered "לא תודה": listed in "הבית" ← "הצעות שדחיתם", where they can be taken after all. */
    val declinedAdvice: StateFlow<Set<String>> = _declinedAdvice

    private fun usageSignals(): app.necessityq.domain.UsageSignals {
        val now = System.currentTimeMillis()
        val month = 30L * 24 * 60 * 60 * 1000
        fun recent(event: String) = prefs.usageEvents(event).count { now - it < month }
        val stats = snapshot.value?.stats?.values.orEmpty()
        val routine = stats.filter { it.classification == app.necessityq.domain.Classification.ROUTINE && it.cadenceDays != null }
            .sortedByDescending { it.confidence }
            .map { app.necessityq.domain.RoutineItem(it.identity.displayName, kotlin.math.round(it.cadenceDays ?: 0.0).toInt()) }
        val repeated = purchases.value.filter { now - it.boughtAtMillis < month }
            .groupBy { it.itemId }
            .map { (_, list) -> list.first().name to list.map { java.time.Instant.ofEpochMilli(it.boughtAtMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }.distinct().size }
            .sortedByDescending { it.second }
        val allFeatures = app.necessityq.domain.Feature.entries
        return app.necessityq.domain.UsageSignals(
            routine = routine,
            repeated = repeated,
            choseStore = currentStoreId.value != null,
            scans = recent("scan"),
            followedBranches = selectedPriceBranches.value.size,
            members = memberNames.value.size.coerceAtLeast(1),
            missingMarks = recent("missing"),
            quickCloses = recent("quick_close"),
            doubleTap = display.value.tapMode == app.necessityq.platform.TapMode.DOUBLE,
            lastUsed = allFeatures.associateWith { prefs.usedAt(it) }.filterValues { it > 0 },
            enabledAt = allFeatures.associateWith { prefs.enabledAt(it) }.filterValues { it > 0 },
        )
    }

    /** Works out the card to show; called when the list opens and after every answer. */
    fun refreshAdvice() {
        // not while the first run is (re)played: the questions come first
        if (!featuresChosen.value) { _advice.value = null; return }
        val state = prefs.advisor
        _declinedAdvice.value = state.declined
        _advice.value = runCatching {
            app.necessityq.domain.FeatureAdvisor.next(features.value, usageSignals(), state, System.currentTimeMillis())
        }.getOrNull()
    }

    /** "להדליק" / "להסתיר" / …: does what the suggestion offers; [openPrices] opens the "מחירים" tab. */
    fun acceptAdvice(s: app.necessityq.domain.FeatureSuggestion, openPrices: () -> Unit) {
        prefs.advisor = prefs.advisor.accepted(System.currentTimeMillis())
        applyAdvice(s.id, openPrices)
        _messages.tryEmit("אפשר לשנות את זה בכל זמן ב\"הבית\" ← \"הגדרות\" ← \"יכולות\"")
    }

    /** Does what the suggestion [id] offers (also from the list of declined suggestions). */
    fun applyAdvice(id: String, openPrices: () -> Unit) {
        val feature = app.necessityq.domain.FeatureAdvisor.featureOf(id)
        when (app.necessityq.domain.FeatureAdvisor.kindOf(id)) {
            app.necessityq.domain.AdviceKind.TURN_ON -> feature?.let { setFeatures(features.value.with(it, true)) }
            app.necessityq.domain.AdviceKind.TURN_OFF -> feature?.let { setFeatures(features.value.with(it, false)) }
            app.necessityq.domain.AdviceKind.DOUBLE_TAP -> setDisplay(display.value.copy(tapMode = app.necessityq.platform.TapMode.DOUBLE))
            app.necessityq.domain.AdviceKind.PICK_BRANCH -> {
                if (app.necessityq.domain.Feature.PRICES_TAB !in features.value) {
                    setFeatures(features.value.with(app.necessityq.domain.Feature.PRICES_TAB, true))
                }
                openPrices()
            }
        }
        refreshAdvice()
    }

    /** "אולי אחר כך": the card goes, and this suggestion may come back in two weeks. */
    fun laterAdvice(s: app.necessityq.domain.FeatureSuggestion) {
        prefs.advisor = prefs.advisor.later(s.id, System.currentTimeMillis())
        refreshAdvice()
    }

    /** "לא תודה": never offered again (it stays in "הצעות שדחיתם"). */
    fun declineAdvice(s: app.necessityq.domain.FeatureSuggestion) {
        prefs.advisor = prefs.advisor.decline(s.id, System.currentTimeMillis())
        refreshAdvice()
    }

    /** Takes a declined suggestion back into the ones that may be offered. */
    fun restoreAdvice(id: String) {
        prefs.advisor = prefs.advisor.restore(id)
        refreshAdvice()
    }

    /** A feature was used (its tab or page opened): it is not offered to be hidden. */
    fun noteUsed(feature: app.necessityq.domain.Feature) = prefs.markUsed(feature, System.currentTimeMillis())

    /** An item's page was closed; open for under two and a half seconds counts as opened by mistake. */
    fun noteItemPageClosed(openedAtMillis: Long) {
        val now = System.currentTimeMillis()
        if (now - openedAtMillis < 2_500L) prefs.addUsageEvent("quick_close", now)
    }

    /**
     * Saves what the tiles of [mode] show. A part shown there whose feature is off (the price, with prices off) turns
     * that feature on for [mode], so what was chosen is what the list shows.
     */
    fun saveTile(mode: app.necessityq.domain.ListMode, layout: app.necessityq.domain.TileLayout) {
        setDisplay(display.value.withTile(mode, layout))
        val allowed = features.value.allowing(layout, mode)
        if (allowed != features.value) setFeatures(allowed)
    }

    /** The swipe hint ("משכו שמאלה…") is shown the first three times the list opens with items. */
    fun swipeHintDue(): Boolean = prefs.swipeHintSeen < 3
    fun countSwipeHint() { prefs.swipeHintSeen = prefs.swipeHintSeen + 1 }
    fun selectStore(id: String?) { prefs.currentStoreId = id; currentStoreId.value = id }

    val stores: StateFlow<List<Store>> = householdId
        .flatMapLatest { hid -> if (hid == null) emptyFlow() else container.collab.stores(hid) }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favorites: StateFlow<Set<String>> = householdId
        .flatMapLatest { hid -> if (hid == null) emptyFlow() else container.collab.favorites(hid) }
        .catch { emit(emptySet()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Unit prices the household entered (until a price database is connected). */
    val prices: StateFlow<Map<String, Double>> = householdId
        .flatMapLatest { hid -> if (hid == null) emptyFlow() else container.collab.prices(hid) }
        .catch { emit(emptyMap()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setPrice(itemId: String, price: Double?) {
        val (hid, _, _) = ctx() ?: return
        launchSafe { container.collab.setPrice(hid, itemId, price?.takeIf { it > 0 }) }
    }

    // ---------- price data (branches near the user, products and price ranges) ----------

    private val _priceBranchesError = MutableStateFlow<String?>(null)
    /** Why the branch list could not be read (null when fine) — shown instead of a silently empty screen. */
    val priceBranchesError: StateFlow<String?> = _priceBranchesError

    val priceBranches: StateFlow<List<app.necessityq.domain.PriceBranch>> = container.prices.branches()
        .onEach { _priceBranchesError.value = null }
        .retryWhen { cause, attempt ->
            _priceBranchesError.value = cause.message ?: cause.javaClass.simpleName
            kotlinx.coroutines.delay(minOf(30_000L, 3_000L * (attempt + 1)))
            true
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The household's followed branches have been read at least once (until then the list above is just a placeholder). */
    private val selectedBranchesKnown = MutableStateFlow(false)

    val selectedPriceBranches: StateFlow<List<String>> = householdId
        .flatMapLatest { hid -> if (hid == null) emptyFlow() else container.prices.selectedBranches(hid) }
        .catch { emit(emptyList()) }
        .onEach { selectedBranchesKnown.value = true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val priceStatus: StateFlow<Map<String, app.necessityq.domain.BranchPriceStatus>> = selectedPriceBranches
        .flatMapLatest { ids -> container.prices.status(ids) }
        .catch { emit(emptyMap()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    // ---------- location by coordinates: where the user is, which branch / household store that is ----------

    /** A device position with its accuracy (metres, when the provider reports one). */
    data class Fix(val latitude: Double, val longitude: Double, val accuracyMeters: Double?)

    private val fix = MutableStateFlow<Fix?>(null)

    /** The device position used to sort branches (null without permission / fix). */
    val userPosition: StateFlow<Pair<Double, Double>?> = fix.map { f -> f?.let { it.latitude to it.longitude } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** A fresh fix is being requested. */
    val locating = MutableStateFlow(false)

    /** Kept for the screens: same as [locate]. */
    fun refreshPosition() = locate(quiet = false)

    /**
     * Requests the current coordinates (not only the last known ones) and from them works out the
     * price branch and the household store the user is at. [quiet] = no message when nothing answers.
     */
    fun locate(quiet: Boolean = true) {
        val context = container.appContext
        if (!LocationHelper.hasPermission(context) || locating.value) return
        if (!LocationHelper.isEnabled(context)) {
            if (!quiet) _messages.tryEmit("המיקום כבוי במכשיר. אפשר להפעיל אותו בהגדרות.")
            LocationHelper.lastKnown(context)?.let(::applyLocation)
            return
        }
        locating.value = true
        viewModelScope.launch {
            // the last known position answers right away; the fresh fix then replaces it
            if (fix.value == null) LocationHelper.lastKnown(context)?.let(::applyLocation)
            val location = runCatching { LocationHelper.current(context) }.getOrNull()
            if (location != null) applyLocation(location)
            else if (!quiet) _messages.tryEmit("לא התקבל מיקום. נסו שוב בעוד רגע, או קרוב יותר לחלון.")
            locating.value = false
        }
    }

    private fun applyLocation(location: android.location.Location) {
        lastLocation = location
        fix.value = Fix(location.latitude, location.longitude, if (location.hasAccuracy()) location.accuracy.toDouble() else null)
    }

    /** The price branch the user is standing in, by coordinates (null when not at a known branch). */
    val hereBranch: StateFlow<app.necessityq.domain.NearbyBranch?> = combine(fix, priceBranches) { f, all ->
        f?.let { app.necessityq.domain.BranchLocator.branchAt(all, it.latitude, it.longitude, it.accuracyMeters) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The branch the offer was turned down for ("לא עכשיו"): not offered again for it in this run. */
    private val shopHereDismissed = MutableStateFlow<String?>(null)

    /**
     * While preparing the list, the location shows a followed branch: "נראה שאתם ב… — לעבור למצב קנייה?",
     * one tap away from shopping there.
     */
    val shopHereOffer: StateFlow<app.necessityq.domain.PriceBranch?> =
        combine(hereBranch, selectedPriceBranches, mode, shopHereDismissed) { here, followed, m, dismissed ->
            here?.branch?.takeIf { m == ListMode.PLAN && it.id in followed && it.id != dismissed }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun dismissShopHere() { shopHereDismissed.value = shopHereOffer.value?.id }

    /** Into shopping at the branch we are standing in: it becomes the store, and a trip left half-way goes on. */
    fun shopHere() {
        val here = shopHereOffer.value ?: return
        if (tripStops.value.isEmpty()) startTrip(null) else setMode(ListMode.SHOP)
        addStoreFromBranch(here)?.let { answerStorePrompt(it) }
    }

    /** Starts following the branch the user is in, so its prices are loaded. */
    fun followHereBranch() {
        val here = hereBranch.value?.branch ?: return
        if (here.id !in selectedPriceBranches.value) toggleBranch(here.id)
    }

    /** A stop of the planned trip the user is shopping at now (the list shows its items and prices). */
    val activeStop = MutableStateFlow(if (prefs.mode == ListMode.SHOP) prefs.tripStops.firstOrNull() else null)

    /** The branch [itemPriceInfo] was last computed for (with the list loaded): the opening waits for it. */
    private val pricedFor = MutableStateFlow<String?>(null)

    /** Loaded product lists per branch. */
    val branchPrices = MutableStateFlow<Map<String, app.necessityq.domain.BranchPrices>>(emptyMap())
    val pricesLoading = MutableStateFlow<Set<String>>(emptySet())
    /** Branches whose prices were asked for at least once and the attempt ended (loaded or failed). */
    private val pricesTried = MutableStateFlow<Set<String>>(emptySet())

    fun toggleBranch(branchId: String) {
        val hid = ctx()?.first ?: return
        val current = selectedPriceBranches.value
        val next = if (branchId in current) current - branchId else current + branchId
        if (next.size > MAX_BRANCHES) {
            _messages.tryEmit("אפשר לעקוב אחרי עד $MAX_BRANCHES סניפים")
            return
        }
        launchSafe {
            container.prices.setSelectedBranches(hid, next)
            // a newly followed branch gets its prices now rather than at the next daily load
            if (branchId !in current) runCatching { container.prices.requestRefresh(branchId) }
        }
    }

    /** Branches whose prices were checked against the server in this session (the cached copy may be shown before). */
    private val pricesChecked = MutableStateFlow<Set<String>>(emptySet())

    /**
     * The products of a branch: the copy cached on the device first, at once (no network on the way), then the
     * server's when it is newer. So the list has its prices as it opens, and fresh ones replace them quietly.
     */
    fun loadPrices(branchId: String, force: Boolean = false) {
        if (!force && (branchId in pricesChecked.value || branchId in pricesLoading.value)) return
        pricesLoading.update { it + branchId }
        viewModelScope.launch {
            if (!branchPrices.value.containsKey(branchId)) {
                runCatching { container.prices.cachedPrices(branchId) }.getOrNull()?.let { cached ->
                    branchPrices.update { if (it.containsKey(branchId)) it else it + (branchId to cached) }
                    pricesTried.update { it + branchId }
                }
            }
            runCatching { container.prices.prices(branchId) }
                .onSuccess { p ->
                    if (p != null && branchPrices.value[branchId]?.updatedAtMillis != p.updatedAtMillis) branchPrices.update { it + (branchId to p) }
                }
                .onFailure { if (!branchPrices.value.containsKey(branchId)) _messages.tryEmit("טעינת המחירים נכשלה: ${it.message ?: ""}") }
            pricesChecked.update { it + branchId }
            pricesTried.update { it + branchId }
            pricesLoading.update { it - branchId }
        }
    }

    /** Asks the server for fresh prices, then reloads them. */
    fun refreshPrices(branchId: String) {
        pricesLoading.update { it + branchId }
        viewModelScope.launch {
            runCatching { container.prices.requestRefresh(branchId) }
                .onSuccess { refreshed -> _messages.tryEmit(if (refreshed) "המחירים עודכנו" else "המחירים כבר עדכניים") }
                .onFailure { _messages.tryEmit("רענון המחירים נכשל: ${it.message ?: ""}") }
            pricesLoading.update { it - branchId }
            loadPrices(branchId, force = true)
        }
    }

    /** Product the household chose per generic item (optional precision). */
    val preferredProducts: StateFlow<Map<String, String>> = householdId
        .flatMapLatest { hid -> if (hid == null) emptyFlow() else container.prices.preferredProducts(hid) }
        .catch { emit(emptyMap()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setPreferredProduct(itemId: String, code: String?) {
        val hid = ctx()?.first ?: return
        launchSafe { container.prices.setPreferredProduct(hid, itemId, code) }
    }

    /**
     * The branch whose prices the list shows: the followed branch the user is standing in (by
     * coordinates), otherwise the first one the household follows.
     */
    val primaryBranchId: StateFlow<String?> = combine(selectedPriceBranches, hereBranch, activeStop) { ids, here, stop ->
        // by id only: the list's prices don't wait for the (large) index of all branches
        here?.branch?.id?.takeIf { it in ids } ?: stop?.takeIf { it in ids } ?: ids.firstOrNull()
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val primaryBranch: StateFlow<app.necessityq.domain.PriceBranch?> = combine(priceBranches, primaryBranchId, hereBranch) { all, id, here ->
        if (id == null) null else here?.branch?.takeIf { it.id == id } ?: all.firstOrNull { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Quick-add suggestions from the products of the branch (online data), as generic items
     * ("קוטג' 5%"), beyond what the household already buys. Empty without price data.
     */
    val onlineCompletions: StateFlow<List<ItemIdentity>> = combine(searchQuery, completions, branchPrices, primaryBranchId) { q, mine, loaded, branch ->
        OnlineSearch(q, mine, branch?.let { loaded[it] }?.products)
    }
        .mapLatest { (q, mine, products) ->
            val name = QuickEntryParser.parse(q)?.item?.displayName ?: q
            if (name.trim().length < 2 || products == null) return@mapLatest emptyList<ItemIdentity>()
            val known = mine.map { it.id }.toSet()
            app.necessityq.domain.PriceData.search(products, name, limit = 60)
                .map { app.necessityq.domain.Scan.genericIdentity(it) }
                .filter { it.id !in known && it.name.isNotBlank() }
                .distinctBy { it.id }
                .take(8)
        }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val indexCache = mutableMapOf<String, app.necessityq.domain.ProductIndex>()

    private fun indexFor(p: app.necessityq.domain.BranchPrices): app.necessityq.domain.ProductIndex {
        synchronized(indexCache) {
            val cached = indexCache[p.branchId]
            if (cached != null && cached.updatedAtMillis == p.updatedAtMillis) return cached
            return app.necessityq.domain.ProductIndex(p.branchId, p.updatedAtMillis, p.products).also { indexCache[p.branchId] = it }
        }
    }

    /**
     * Price picture per list item at the primary branch: typical range of the matching products
     * ("קוטג'" → ₪5.90–₪7.80), or the household's chosen product. Computed off the main thread.
     */
    val itemPriceInfo: StateFlow<Map<String, app.necessityq.domain.ItemPriceInfo>> =
        combine(snapshot, branchPrices, primaryBranchId, preferredProducts) { snap, loaded, primary, preferred -> PriceInputs(snap, loaded, primary, preferred) }
            .mapLatest { (snap, loaded, primary, preferred) ->
                val prices = primary?.let { loaded.at(it) } ?: return@mapLatest emptyMap()
                val index = indexFor(prices)
                snap?.lines.orEmpty().distinctBy { it.itemId }.mapNotNull { line ->
                    app.necessityq.domain.ItemPrices.info(index, line.name, line.variant, preferred[line.itemId])?.let { line.itemId to it }
                }.toMap().also { if (snap != null) pricedFor.value = primary }
            }
            .flowOn(kotlinx.coroutines.Dispatchers.Default)
            .catch { emit(emptyMap()) }
            // eager: computed while the opening screen still shows, so the list opens with its prices
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Price picture of any item (also one not on the list right now, e.g. from history). Computed off the main thread. */
    suspend fun priceInfoFor(itemId: String, name: String, variant: String?): app.necessityq.domain.ItemPriceInfo? {
        itemPriceInfo.value[itemId]?.let { return it }
        val primary = primaryBranchId.value ?: return null
        val prices = branchPrices.value.at(primary) ?: return null
        val preferred = preferredProducts.value[itemId]
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            app.necessityq.domain.ItemPrices.info(indexFor(prices), name, variant, preferred)
        }
    }

    private val codesCache = mutableMapOf<String, Pair<Long, Map<String, app.necessityq.domain.BranchProduct>>>()

    /** Products of a branch by barcode (cached per data version), for comparing the same product across branches. */
    private fun codesFor(p: app.necessityq.domain.BranchPrices): Map<String, app.necessityq.domain.BranchProduct> =
        synchronized(codesCache) {
            codesCache[p.branchId]?.takeIf { it.first == p.updatedAtMillis }?.second
                ?: app.necessityq.domain.PriceComparisons.byCode(p.products).also { codesCache[p.branchId] = p.updatedAtMillis to it }
        }

    /** The other followed branches whose prices are loaded (all but [branchId]). */
    private fun otherLoaded(branchId: String, loaded: Map<String, app.necessityq.domain.BranchPrices>, followed: List<String>) =
        followed.filter { it != branchId }.mapNotNull { id -> loaded.at(id) }

    /** A loaded branch, keyed by the id it was loaded under (the comparisons key branches by it). */
    private fun Map<String, app.necessityq.domain.BranchPrices>.at(id: String) =
        get(id)?.let { if (it.branchId == id) it else it.copy(branchId = id) }

    private fun compare(
        here: app.necessityq.domain.ItemPriceInfo,
        name: String,
        variant: String?,
        preferredCode: String?,
        others: List<app.necessityq.domain.BranchPrices>,
    ): app.necessityq.domain.PriceComparison? {
        if (others.isEmpty()) return null
        val infos = others.associate { bp -> bp.branchId to app.necessityq.domain.ItemPrices.info(indexFor(bp), name, variant, preferredCode) }
        return app.necessityq.domain.PriceComparisons.item(here, infos, others.associate { it.branchId to codesFor(it) })
    }

    /**
     * Each list item at the primary branch compared with the household's other followed branches ("the stores we
     * usually buy in"): significantly dearer or cheaper is marked on the tile. Computed off the main thread.
     */
    val itemComparisons: StateFlow<Map<String, app.necessityq.domain.PriceComparison>> =
        combine(itemPriceInfo, branchPrices, selectedPriceBranches, preferredProducts) { infos, loaded, followed, preferred ->
            CompareInputs(infos, loaded, followed, preferred)
        }
            .mapLatest { (infos, loaded, followed, preferred) ->
                val lines = snapshot.value?.lines.orEmpty().distinctBy { it.itemId }.associateBy { it.itemId }
                infos.mapNotNull { (itemId, here) ->
                    val line = lines[itemId] ?: return@mapNotNull null
                    compare(here, line.name, line.variant, preferred[itemId], otherLoaded(here.branchId, loaded, followed))?.let { itemId to it }
                }.toMap()
            }
            .flowOn(kotlinx.coroutines.Dispatchers.Default)
            .catch { emit(emptyMap()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** An item (also one not on the list) compared with the household's other followed branches. Off the main thread. */
    suspend fun comparisonFor(itemId: String, name: String, variant: String?): app.necessityq.domain.PriceComparison? {
        itemComparisons.value[itemId]?.let { return it }
        val here = priceInfoFor(itemId, name, variant) ?: return null
        val others = otherLoaded(here.branchId, branchPrices.value, selectedPriceBranches.value)
        val preferred = preferredProducts.value[itemId]
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { compare(here, name, variant, preferred, others) }
    }

    /**
     * Product search over several followed branches at once, merged by barcode with the price at each branch
     * (the products tab of "סניפים ומחירים"). Off the main thread.
     */
    suspend fun searchProducts(branchIds: List<String>, query: String): List<app.necessityq.domain.ProductAcross> {
        val loaded = branchPrices.value
        val chosen = branchIds.mapNotNull { id -> loaded.at(id) }
        if (chosen.isEmpty() || query.isBlank()) return emptyList()
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            app.necessityq.domain.ProductSearch.across(chosen.map { it.branchId to it.products }, chosen.associate { it.branchId to codesFor(it) }, query)
        }
    }

    /**
     * [searchProducts] in steps: the first hits come at once (the quick pass over each branch), then more are added
     * as the slower pass finishes. Collecting stops the work (a new query cancels it). Off the main thread.
     */
    fun searchProductsProgressive(branchIds: List<String>, query: String): kotlinx.coroutines.flow.Flow<List<app.necessityq.domain.ProductAcross>> = kotlinx.coroutines.flow.flow {
        val loaded = branchPrices.value
        val chosen = branchIds.mapNotNull { id -> loaded.at(id) }
        if (chosen.isEmpty() || query.isBlank()) { emit(emptyList()); return@flow }
        val byId = chosen.associateBy { it.branchId }
        val indexes = chosen.map { it.branchId to indexFor(it) }
        val stages = app.necessityq.domain.ProductSearch.stages(indexes, { id -> byId[id]?.let(::codesFor).orEmpty() }, query)
        for (step in stages) {
            emit(step)
        }
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)

    /** One product at [branchId] against the same barcode at the household's other followed branches. Off the main thread. */
    suspend fun compareProducts(
        products: List<app.necessityq.domain.BranchProduct>,
        branchId: String,
    ): Map<String, app.necessityq.domain.PriceComparison> {
        val others = otherLoaded(branchId, branchPrices.value, selectedPriceBranches.value)
        if (others.isEmpty()) return emptyMap()
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val codes = others.associate { it.branchId to codesFor(it) }
            products.mapNotNull { p -> app.necessityq.domain.PriceComparisons.product(p, branchId, codes)?.let { p.code to it } }.toMap()
        }
    }

    // ---------- promotions and coupons; where to shop (one supermarket or a split) ----------

    /** Running promotions per followed branch (only products in stock there; see [app.necessityq.domain.PromoOffers]). */
    val branchPromotions = MutableStateFlow<Map<String, app.necessityq.domain.BranchPromotions>>(emptyMap())

    /** Promotions of clubs, credit cards and coupons count too (the household chooses). */
    val includeMemberPromos = MutableStateFlow(prefs.tripMembers)
    fun setIncludeMemberPromos(on: Boolean) { prefs.tripMembers = on; includeMemberPromos.value = on }

    /** Branches whose promotions were tried at least once (loaded, or failed with nothing cached): the opening waits for the primary one. */
    private val promosTried = MutableStateFlow<Set<String>>(emptySet())

    /** The primary branch the promotions were matched to the list's items for (the opening screen waits for it). */
    private val promosMatchedFor = MutableStateFlow<String?>(null)

    private fun loadPromotions(branchId: String) {
        viewModelScope.launch {
            runCatching { container.prices.promotions(branchId) }.getOrNull()?.let { p ->
                if (branchPromotions.value[branchId]?.updatedAtMillis != p.updatedAtMillis) branchPromotions.update { it + (branchId to p) }
            }
            promosTried.update { it + branchId }
        }
    }

    private var lastOffersRefresh = 0L

    /**
     * Promotions and stock: on every opening of the app and every half hour while it is open. The server checks the
     * chains' newest files (it skips branches checked in the last 30 minutes); what changed is reloaded here as its
     * status changes ([promotionsStatus] / the price status).
     */
    fun refreshOffers(force: Boolean = false) {
        val ids = selectedPriceBranches.value
        if (ids.isEmpty()) return
        val now = System.currentTimeMillis()
        if (!force && now - lastOffersRefresh < OFFERS_MINUTES * 60_000L - 60_000L) return
        lastOffersRefresh = now
        viewModelScope.launch {
            runCatching { container.prices.requestOffersRefresh(ids) }
            ids.forEach { loadPromotions(it) }
            // stock and prices merged by the check: reload what changed (only downloads when the server's copy is newer)
            ids.forEach { loadPrices(it, force = true) }
        }
    }

    /** Each followed branch's price picture of the list's items (for the promotions and the trip plan). Off the main thread. */
    private val branchItemInfos: StateFlow<Map<String, Map<String, app.necessityq.domain.ItemPriceInfo>>> =
        combine(snapshot, branchPrices, selectedPriceBranches, preferredProducts) { snap, loaded, followed, preferred ->
            BranchInfoInputs(snap?.lines.orEmpty().filter { it.status == LineStatus.NEEDED }, loaded, followed, preferred)
        }
            .debounce(300)
            .mapLatest { (lines, loaded, followed, preferred) ->
                val distinct = lines.distinctBy { it.itemId }
                followed.mapNotNull { id -> loaded.at(id) }.associate { bp ->
                    val index = indexFor(bp)
                    bp.branchId to distinct.mapNotNull { line ->
                        app.necessityq.domain.ItemPrices.info(index, line.name, line.variant, preferred[line.itemId])?.let { line.itemId to it }
                    }.toMap()
                }
            }
            .flowOn(kotlinx.coroutines.Dispatchers.Default)
            .catch { emit(emptyMap()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** "Now" in Israel as the promotion files write it ("yyyy-MM-ddTHH:mm:ss"). */
    private fun nowLocal(): String = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Jerusalem"))
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))

    /** "יש למוצרים אלו מבצעים ב…": promotions on the list's items at the followed branches, biggest saving first. */
    val promoOffers: StateFlow<List<app.necessityq.domain.PromoOffer>> =
        combine(branchItemInfos, branchPromotions, includeMemberPromos, promosTried) { infos, promos, members, tried -> PromoInputs(infos, promos, members, tried) }
            .mapLatest { (infos, promos, members, tried) ->
                val lines = snapshot.value?.lines.orEmpty()
                app.necessityq.domain.PromoOffers.forList(lines, infos, promos, nowLocal(), members).also {
                    // matched to the list's items after the primary branch's promotions arrived: the opening may finish
                    val primary = primaryBranchId.value
                    if (primary != null && primary in tried && snapshot.value != null) promosMatchedFor.value = primary
                }
            }
            .flowOn(kotlinx.coroutines.Dispatchers.Default)
            .catch { emit(emptyList()) }
            // eager: computed while the opening screen still shows, so the list opens with its promotions
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Whole-basket promotions of the followed branches ("10 ₪ הנחה בקנייה מעל 300 ₪"), as (branch id, promotion). */
    val basketOffers: StateFlow<List<Pair<String, app.necessityq.domain.Promotion>>> =
        combine(branchPromotions, includeMemberPromos, selectedPriceBranches) { promos, members, followed ->
            app.necessityq.domain.PromoOffers.basketOffers(promos.filterKeys { it in followed }, nowLocal(), members)
        }
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** What the trip sheet asks: the saving that makes a change worth it, how many stores, the regular store. */
    data class TripSettings(val threshold: Int, val maxStores: Int, val homeBranchId: String?, val includeMembers: Boolean)

    val tripSettings = MutableStateFlow(TripSettings(prefs.tripThreshold, prefs.tripMaxStores, prefs.tripHome, prefs.tripMembers))

    fun setTripSettings(value: TripSettings) {
        prefs.tripThreshold = value.threshold
        prefs.tripMaxStores = value.maxStores
        prefs.tripHome = value.homeBranchId
        setIncludeMemberPromos(value.includeMembers)
        tripSettings.value = value
    }

    /** The regular supermarket: as chosen, else the first followed branch. */
    fun homeBranchId(settings: TripSettings = tripSettings.value): String? =
        settings.homeBranchId?.takeIf { it in selectedPriceBranches.value } ?: selectedPriceBranches.value.firstOrNull()

    /**
     * The trip plan for the open lines: where it pays to shop by [settings] (see [app.necessityq.domain.ShoppingPlanner]).
     * Null with fewer than two branches loaded or nothing comparable. Off the main thread.
     */
    val tripPlan: StateFlow<app.necessityq.domain.TripPlan?> =
        combine(branchItemInfos, branchPromotions, tripSettings, selectedPriceBranches) { infos, promos, settings, followed ->
            TripInputs(infos, promos, settings, followed)
        }
            .mapLatest { (infos, promos, settings, followed) ->
                val home = settings.homeBranchId?.takeIf { it in followed } ?: followed.firstOrNull() ?: return@mapLatest null
                if (infos.size < 2) return@mapLatest null
                val lines = snapshot.value?.lines.orEmpty().filter { it.status == LineStatus.NEEDED }.distinctBy { it.itemId }
                val now = nowLocal()
                val quotes = infos.mapValues { (branchId, byItem) ->
                    lines.mapNotNull { line ->
                        byItem[line.itemId]?.let { info ->
                            app.necessityq.domain.ShoppingPlanner.quote(line, info, promos[branchId], now, settings.includeMembers)
                        }?.let { line.id to it }
                    }.toMap()
                }
                val basket = infos.keys.associateWith { id ->
                    app.necessityq.domain.PromoOffers.basket(promos[id], now, settings.includeMembers)
                }
                app.necessityq.domain.ShoppingPlanner.plan(lines, quotes, home, settings.threshold.toDouble(), settings.maxStores, basket)
            }
            .flowOn(kotlinx.coroutines.Dispatchers.Default)
            .catch { emit(null) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The split the trip follows: item id → branch; empty when not split. */
    val tripAssignment = MutableStateFlow(prefs.tripAssignment)
    /** The stops of the trip, in order (one stop = a single supermarket). */
    val tripStops = MutableStateFlow(prefs.tripStops)

    /**
     * Out to shop with [plan]'s advice: a split divides the list into one list per stop (the first stop shown first);
     * one supermarket shows its prices. Null = shop as usual, without a plan.
     */
    fun startTrip(plan: app.necessityq.domain.TripPlan?) {
        val stops = plan?.chosen?.branchIds.orEmpty()
        val assignment = if (stops.size > 1) plan!!.assignment else emptyMap()
        prefs.tripAssignment = assignment
        prefs.tripStops = stops
        tripAssignment.value = assignment
        tripStops.value = stops
        activeStop.value = stops.firstOrNull()
        // the supermarket chosen while planning is where we shop: "איפה קונים היום?" is not asked again
        stops.firstOrNull()?.let { shopAtBranch(it) }
        setMode(ListMode.SHOP)
    }

    /**
     * "סיום קנייה": the only way a trip ends. Says in one line what happened (bought · missing, which stay on
     * the list · not bought), moves what was bought to the recycle bin (with "ביטול"), ends the split and
     * goes back to preparing the list.
     */
    fun finishShopping() {
        val summary = app.necessityq.domain.TripSummary.of(snapshot.value?.lines.orEmpty())
        endTrip()
        setMode(ListMode.PLAN)
        if (summary.bought > 0) clearBought(summary.text()) else _messages.tryEmit(summary.text())
    }

    /** Shows one stop of the split (its items and prices); null = the whole list. The store follows the stop. */
    fun selectStop(branchId: String?) {
        activeStop.value = branchId
        branchId?.let { shopAtBranch(it) }
    }

    /** Makes the followed branch [branchId] the current store (the household's store of that name, saved if new). */
    private fun shopAtBranch(branchId: String) {
        val branch = priceBranches.value.firstOrNull { it.id == branchId } ?: return
        addStoreFromBranch(branch)?.let { answerStorePrompt(it) }
    }

    /** The household store that stands for [branch] (same name), if there is one. */
    fun storeOfBranch(branch: app.necessityq.domain.PriceBranch, stores: List<Store> = this.stores.value): Store? {
        val key = HebrewText.matchKey(app.necessityq.domain.BranchLocator.storeName(branch))
        return stores.firstOrNull { HebrewText.matchKey(it.name) == key }
    }

    private fun endTrip() {
        prefs.tripAssignment = emptyMap()
        prefs.tripStops = emptyList()
        tripAssignment.value = emptyMap()
        tripStops.value = emptyList()
        activeStop.value = null
    }

    init {
        // the branch the list showed last time: its cached prices are read right away, while the account and the list
        // are still loading, so they are ready when the list is
        prefs.lastPriceBranch?.let { loadPrices(it) }
        // the list shows ranges of the primary branch: load its prices as soon as it is known
        viewModelScope.launch {
            primaryBranchId.collect { id ->
                if (id != null) {
                    loadPrices(id)
                    loadPromotions(id) // in parallel with the server's check, so the opening has them (the cached ones at least)
                    if (activeStop.value == null && hereBranch.value == null) prefs.lastPriceBranch = id
                }
            }
        }
        // the other followed branches too (in the background), so items can be compared with them
        viewModelScope.launch { selectedPriceBranches.collect { ids -> ids.forEach { loadPrices(it) } } }
        // promotions: when the followed branches are known (the opening), then whenever the server reloads them
        viewModelScope.launch {
            selectedPriceBranches.filter { it.isNotEmpty() }.first()
            refreshOffers(force = true)
        }
        viewModelScope.launch {
            selectedPriceBranches.flatMapLatest { ids -> container.prices.promotionsStatus(ids) }
                .catch { emit(emptyMap()) }
                .collect { status -> status.keys.forEach { loadPromotions(it) } }
        }
        // every half hour while the app is open
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(OFFERS_MINUTES * 60_000L)
                refreshOffers()
            }
        }
    }

    /**
     * How far the opening load got: the app opens only once the account, the list and the prices of the household's
     * branch are in, so the first screen already shows items with their prices rather than filling in piece by piece.
     * A failure does not hold the opening (offline the list comes from the local copy, prices from the cached file).
     */
    val startupStep: StateFlow<StartupStep> = combine(
        session, snapshot, selectedBranchesKnown,
        combine(primaryBranchId, branchPrices, pricesTried, pricedFor) { id, loaded, tried, priced -> PriceReady(id, id != null && id in loaded, id != null && id in tried, priced) },
        promosMatchedFor,
    ) { s, snap, branchesKnown, ready, promosFor ->
        val household = (s as? Session.SignedIn)?.household
        when {
            s is Session.Loading -> StartupStep.ACCOUNT
            household == null -> StartupStep.READY // an error or a new user (onboarding): nothing to load
            snap == null -> StartupStep.LIST
            !branchesKnown -> StartupStep.PRICES
            ready.branchId == null -> StartupStep.READY
            // loaded and already matched to the list's items: the first screen shows the prices
            // (and the promotions matched too, so the gift marks and the strips are there from the first frame)
            ready.loaded && ready.pricedFor == ready.branchId -> if (promosFor == ready.branchId) StartupStep.READY else StartupStep.PRICES
            // nothing cached and the download failed: open without prices
            !ready.loaded && ready.tried -> StartupStep.READY
            else -> StartupStep.PRICES
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StartupStep.ACCOUNT)

    /** Shopping day: branches whose prices are older than 12 hours are refreshed in the background. */
    private fun refreshStalePricesForShopping() {
        val now = System.currentTimeMillis()
        val stale = selectedPriceBranches.value.filter { id ->
            val updated = priceStatus.value[id]?.updatedAtMillis ?: 0L
            now - updated > 12 * 3_600_000L
        }
        if (stale.isEmpty()) return
        viewModelScope.launch {
            stale.forEach { id -> runCatching { container.prices.requestRefresh(id) } }
            stale.filter { branchPrices.value.containsKey(it) }.forEach { loadPrices(it, force = true) }
        }
    }

    /** A known household store at the device position (by coordinates; follows [locate]). */
    val nearbyStore: StateFlow<Store?> = combine(fix, stores) { f, list ->
        f?.let { Stores.nearest(list, it.latitude, it.longitude, maxOf(250.0, minOf(it.accuracyMeters ?: 0.0, 400.0))) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private var lastLocation: android.location.Location? = null

    fun refreshLocation() = locate()

    /**
     * The household store of a price branch ("רמי לוי תלפיות"): one store per branch, so a followed branch and
     * "the store we shop at" are the same thing. Saved (at the current coordinates, or the branch's own when there
     * is no fix) the first time it is chosen; afterwards its id is returned.
     */
    fun addStoreFromBranch(branch: app.necessityq.domain.PriceBranch): String? {
        storeOfBranch(branch)?.let { return it.id }
        val (hid, _, _) = ctx() ?: return null
        // an exactly geocoded branch keeps its own coordinates (it may be chosen from afar, from the
        // followed list); one known only by its city centre takes where the user is shopping now
        val exact = !branch.approximate && branch.latitude != null && branch.longitude != null
        val bLat = branch.latitude
        val bLon = branch.longitude
        // the current position only when it is plausibly in the branch's city (within 10 km of its centre)
        val location = lastLocation?.takeIf { l ->
            bLat == null || bLon == null || Stores.distanceMeters(l.latitude, l.longitude, bLat, bLon) <= 10_000
        }
        val store = Store(
            id = java.util.UUID.randomUUID().toString(),
            name = app.necessityq.domain.BranchLocator.storeName(branch),
            type = StoreType.SUPERMARKET,
            latitude = if (exact) bLat else location?.latitude ?: bLat,
            longitude = if (exact) bLon else location?.longitude ?: bLon,
        )
        launchSafe { container.collab.saveStore(hid, store) }
        return store.id
    }

    fun hasLocationPermission() = LocationHelper.hasPermission(container.appContext)

    /** Saves a new store at the last known position and makes it the current one. */
    fun addStore(name: String, type: StoreType) {
        val (hid, _, _) = ctx() ?: return
        val location = lastLocation ?: LocationHelper.lastKnown(container.appContext)
        val store = Store(
            id = java.util.UUID.randomUUID().toString(),
            name = name.trim().ifBlank { return },
            type = type,
            latitude = location?.latitude,
            longitude = location?.longitude,
        )
        launchSafe {
            container.collab.saveStore(hid, store)
            selectStore(store.id)
            if (location == null) _messages.tryEmit("החנות נשמרה בלי מיקום (אין הרשאת מיקום)")
        }
    }

    fun addByName(name: String) {
        val (hid, uid, lines) = ctx() ?: return
        launchSafe { container.service.addFromText(hid, uid, name, lines) }
    }

    fun toggleFavorite(itemId: String) {
        val (hid, _, _) = ctx() ?: return
        val now = itemId in favorites.value
        launchSafe { container.collab.setFavorite(hid, itemId, !now) }
    }

    fun adjustQuantity(line: ListLine, delta: Int) {
        val (hid, _, _) = ctx() ?: return
        launchSafe { container.service.adjustQuantity(hid, line, delta) }
    }

    fun moveLine(line: ListLine, category: app.necessityq.domain.Category, previous: ListLine?, next: ListLine?) {
        val (hid, _, _) = ctx() ?: return
        launchSafe { container.service.moveLine(hid, line, category, previous, next) }
    }

    fun setMissing(line: ListLine, missing: Boolean) {
        val (hid, uid, _) = ctx() ?: return
        if (missing) askNotificationsInContext()
        if (missing) prefs.addUsageEvent("missing", System.currentTimeMillis())
        launchSafe { container.service.setMissing(hid, uid, line, missing) }
        if (missing) _undo.tryEmit(UndoEvent("${line.displayName} סומן כחסר") { setMissing(line, false) })
    }

    // ---------- barcode scanning ----------

    /** The last scan, shown in the scan sheet (null = closed). */
    val scanResult = MutableStateFlow<app.necessityq.domain.ScanResult?>(null)
    /** A scanned code is being looked up (prices may have to be downloaded first). */
    val scanBusy = MutableStateFlow(false)

    /** Prices of every followed branch, downloading the ones not loaded yet. */
    private suspend fun followedPrices(): Map<String, app.necessityq.domain.BranchPrices> {
        val ids = selectedPriceBranches.value
        ids.filter { !branchPrices.value.containsKey(it) }.forEach { id ->
            runCatching { container.prices.prices(id) }.getOrNull()?.let { p -> branchPrices.update { it + (id to p) } }
        }
        return branchPrices.value.filterKeys { it in ids }
    }

    private suspend fun lookupScan(raw: String): app.necessityq.domain.ScanResult? {
        val loaded = followedPrices()
        val snap = snapshot.value
        val branchId = primaryBranch.value?.id
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            app.necessityq.domain.Scan.lookup(
                raw,
                branchId,
                loaded,
                snap?.lines.orEmpty(),
                snap?.stats?.values?.map { it.identity }.orEmpty(),
                preferredProducts.value,
            )
        }
    }

    /** A code read by the camera: price at the branch you are in, and what it is on the list. */
    fun onScanned(raw: String?) {
        if (raw == null) return
        prefs.addUsageEvent("scan", System.currentTimeMillis())
        if (app.necessityq.domain.Barcodes.normalize(raw) == null) {
            _messages.tryEmit("זה לא נראה כמו ברקוד של מוצר: $raw")
            return
        }
        if (fix.value == null || mode.value == ListMode.SHOP) locate()
        scanBusy.value = true
        viewModelScope.launch {
            runCatching { lookupScan(raw) }
                .onSuccess { scanResult.value = it }
                .onFailure { _messages.tryEmit("החיפוש נכשל: ${it.message ?: ""}") }
            scanBusy.value = false
        }
    }

    fun closeScan() { scanResult.value = null }

    /**
     * "Remind me later" for a scanned product: kept on this device for now — how and when the
     * reminder appears will be defined later.
     */
    fun remindLater(result: app.necessityq.domain.ScanResult) {
        val p = result.product
        val name = p?.name ?: result.identity?.displayName ?: result.code
        prefs.addRemindLater(
            app.necessityq.domain.ScanReminder(
                code = result.code,
                name = name,
                price = result.here?.price ?: p?.price,
                branchId = result.branchId,
                atMillis = System.currentTimeMillis(),
                itemName = result.identity?.displayName,
            ),
        )
        scanReminders.value = prefs.remindLater
        _messages.tryEmit("נשמר לתזכורת: $name — יופיע ב\"לבדוק שוב\"")
        closeScan()
    }

    /** "לבדוק שוב": products saved with "תזכיר לי אחר כך" while scanning (newest last). */
    val scanReminders = MutableStateFlow(prefs.remindLater)

    /** "לרשימה" on a reminder: the item goes on the list and the reminder is done. */
    fun reminderToList(reminder: app.necessityq.domain.ScanReminder) {
        val (hid, uid, lines) = ctx() ?: return
        launchSafe { container.service.addFromText(hid, uid, reminder.listName, lines)?.let { _added.tryEmit(it.item.id) } }
        dismissReminder(reminder)
    }

    fun dismissReminder(reminder: app.necessityq.domain.ScanReminder) {
        prefs.removeRemindLater(reminder.code)
        scanReminders.value = prefs.remindLater
    }

    /** The scanned product is on the list: mark that line bought. */
    fun scanCheckOff(result: app.necessityq.domain.ScanResult) {
        val line = result.line?.takeIf { it.status == LineStatus.NEEDED } ?: return
        checkOff(line)
        scanResult.value = result.copy(line = line.copy(status = LineStatus.BOUGHT))
    }

    /** Adds the scanned product to the list as the generic item [text]; optionally remembers the exact product. */
    fun scanAdd(result: app.necessityq.domain.ScanResult, text: String, rememberProduct: Boolean) {
        val (hid, uid, lines) = ctx() ?: return
        val code = result.product?.code
        closeScan()
        launchSafe {
            val parsed = container.service.addFromText(hid, uid, text, lines) ?: return@launchSafe
            if (rememberProduct && code != null) container.prices.setPreferredProduct(hid, parsed.item.id, code)
            _added.tryEmit(parsed.item.id)
            _messages.tryEmit("${parsed.item.displayName} נוסף לרשימה")
        }
    }

    /** Remembers the scanned product as the household's product for its generic item. */
    fun scanRemember(result: app.necessityq.domain.ScanResult) {
        val identity = result.identity ?: return
        val code = result.product?.code ?: return
        setPreferredProduct(identity.id, code)
        scanResult.value = result.copy(alreadyPreferred = true)
    }

    /** Scanned from an item's page: that exact product becomes the item's chosen product. */
    /**
     * "לרשימה" on a product found on the prices tab: the list gets the generic item ("קוטג' 5%"), and the exact
     * product is remembered for it, so its price is the one shown.
     */
    fun addProductToList(product: app.necessityq.domain.BranchProduct) {
        val (hid, uid, lines) = ctx() ?: return
        val item = app.necessityq.domain.Scan.genericIdentity(product)
        launchSafe {
            container.service.add(hid, uid, item, null, LineSource.MANUAL, lines)
            setPreferredProduct(item.id, product.code)
            _added.tryEmit(item.id)
            _messages.tryEmit("${item.displayName} נוסף לרשימה (${product.name})")
        }
    }

    fun linkScannedProduct(itemId: String, raw: String?) {
        if (raw == null) return
        scanBusy.value = true
        viewModelScope.launch {
            val code = app.necessityq.domain.Barcodes.normalize(raw)
            val product = runCatching { lookupScan(raw) }.getOrNull()?.product
            when {
                code == null -> _messages.tryEmit("זה לא נראה כמו ברקוד של מוצר: $raw")
                product == null -> {
                    // remembered anyway: the price appears once a followed branch lists the barcode
                    setPreferredProduct(itemId, code)
                    _messages.tryEmit("הברקוד נשמר למוצר. עוד אין לו מחיר בסניפים שאתם עוקבים אחריהם.")
                }
                else -> {
                    setPreferredProduct(itemId, product.code)
                    _messages.tryEmit("נשמר: ${product.name} — יזכר גם כשהמוצר יתווסף שוב")
                }
            }
            scanBusy.value = false
        }
    }

    // ---------- item conversations, notifications ----------

    val me: String get() = (session.value as? Session.SignedIn)?.userId.orEmpty()

    private val allMessages: StateFlow<List<ItemMessage>> = householdId
        .flatMapLatest { hid -> if (hid == null) emptyFlow() else container.collab.messages(hid) }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Conversations of the current shopping event (what the list and the messages screen show). */
    val itemMessages: StateFlow<List<ItemMessage>> = allMessages
        .map { all -> all.filter { !it.isArchived } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Conversations of finished shopping events, kept until their retention period ends. */
    val archivedMessages: StateFlow<List<ItemMessage>> = allMessages
        .map { all -> all.filter { it.isArchived } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Days an ended conversation is kept; null until confirmed by the server. */
    val chatRetention: StateFlow<Int?> = householdId
        .flatMapLatest { hid -> if (hid == null) emptyFlow() else container.collab.chatRetentionDays(hid) }
        .catch { }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** What is left of deleted conversations, per item. */
    val chatStats: StateFlow<Map<String, app.necessityq.domain.ChatStats>> = householdId
        .flatMapLatest { hid -> if (hid == null) emptyFlow() else container.collab.chatStats(hid) }
        .catch { emit(emptyMap()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setChatRetention(days: Int) {
        val (hid, _, _) = ctx() ?: return
        launchSafe { container.collab.setChatRetentionDays(hid, days) }
    }

    val notifySettings: StateFlow<NotifySettings> = householdId
        .flatMapLatest { hid -> if (hid == null) emptyFlow() else container.collab.notifySettings(hid) }
        .catch { emit(NotifySettings()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, NotifySettings())

    /**
     * The household's accounts, each named as the family calls them: an account linked to a family profile
     * takes the profile's name, so a rename shows at once for everyone, in the family and in צ'אט פוד.
     */
    private val namedMembers: kotlinx.coroutines.flow.Flow<List<Member>> =
        combine(container.household.members, meals) { list, data -> app.necessityq.domain.Members.withFamilyNames(list, data.members) }
            .distinctUntilChanged()

    /** uid → display name, for "added by" and chat. */
    val memberNames: StateFlow<Map<String, String>> = namedMembers
        .map { list -> list.associate { it.userId to it.displayName } }
        .catch { emit(emptyMap()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Profile pictures of the members (decoded once), for the avatars in צ'אט פוד. */
    val memberPhotos: StateFlow<Map<String, androidx.compose.ui.graphics.ImageBitmap>> = container.household.members
        .map { list -> list.mapNotNull { m -> m.photo }.joinToString() to list }
        .distinctUntilChanged { a, b -> a.first == b.first }
        .map { (_, list) ->
            list.mapNotNull { m ->
                val bmp = m.photo?.let { app.necessityq.platform.AvatarCodec.decode(it) } ?: return@mapNotNull null
                m.userId to bmp.asImageBitmap()
            }.toMap()
        }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .catch { emit(emptyMap()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** צ'אט פוד: the household's members that can be tagged with "@". */
    val chatMembers: StateFlow<List<app.necessityq.domain.ChatMember>> = memberNames
        .map { names -> names.map { (id, name) -> app.necessityq.domain.ChatMember(id, name) }.sortedBy { it.name } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun mentionsIn(text: String, author: String): List<String> =
        app.necessityq.domain.Mentions.resolve(text, chatMembers.value, author)

    /**
     * After sending a message that tags someone or quotes their message: ask the server to push it to
     * them (with sound even on silent). While the message has not reached the server (sent offline)
     * the request fails, so it is retried for a few minutes.
     */
    private fun pushAlerts(hid: String, message: ItemMessage) {
        val push = container.chatPush ?: return
        val quoted = message.replyToId?.let { id -> allMessages.value.firstOrNull { it.id == id }?.authorId }
        if (app.necessityq.domain.ChatAlerts.targets(message, quoted).isEmpty() && message.replyToId == null) return
        viewModelScope.launch {
            for (wait in listOf(0L, 4_000L, 15_000L, 45_000L, 120_000L, 300_000L)) {
                kotlinx.coroutines.delay(wait)
                if (runCatching { push.alert(hid, message.id) }.isSuccess) return@launch
            }
        }
    }

    /** Bumped when a conversation is opened so unread counters recompute. */
    private val readTick = MutableStateFlow(0)

    fun unreadCount(itemId: String, all: List<ItemMessage>): Int {
        val readAt = prefs.readAt(itemId)
        return all.count { it.itemId == itemId && it.authorId != me && it.atMillis > readAt }
    }

    val unreadTotal: StateFlow<Int> = combine(itemMessages, readTick) { all, _ ->
        all.filter { it.authorId != me && it.atMillis > prefs.readAt(it.itemId) }.size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun markRead(itemId: String) {
        val now = System.currentTimeMillis()
        prefs.markRead(itemId, now)
        readTick.value++
        val (hid, uid, _) = ctx() ?: return
        launchSafe { container.collab.markRead(hid, uid, itemId, now) }
    }

    val receipts: StateFlow<Map<String, app.necessityq.domain.Receipt>> = householdId
        .flatMapLatest { hid -> if (hid == null) emptyFlow() else container.collab.receipts(hid) }
        .catch { emit(emptyMap()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun deliveryStatus(m: ItemMessage): app.necessityq.domain.DeliveryStatus =
        app.necessityq.domain.Receipts.status(m, me, receipts.value, memberNames.value.keys)

    private var lastSeenReported = 0L

    private fun authorName(uid: String) =
        memberNames.value[uid] ?: (session.value as? Session.SignedIn)?.account?.displayName ?: "אני"

    // ---------- "typing…" in item conversations ----------

    /** Ticks every second so typing marks expire on screen even when nothing else changes. */
    private val clockTick = kotlinx.coroutines.flow.flow {
        while (true) {
            emit(System.currentTimeMillis())
            kotlinx.coroutines.delay(1_000)
        }
    }

    /** Other members typing right now, in any item conversation (only while connected: marks are live). */
    val typingNow: StateFlow<List<app.necessityq.domain.TypingState>> = householdId
        .flatMapLatest { hid -> if (hid == null) emptyFlow() else container.collab.typing(hid) }
        .catch { emit(emptyList()) }
        .let { marks -> combine(marks, clockTick) { all, now -> app.necessityq.domain.Typing.active(all, me, now) } }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var typingItem: String? = null
    private var typingAnnouncedAt = 0L
    private var typingIdle: kotlinx.coroutines.Job? = null

    /** The text box of an item conversation changed: announce typing (throttled), stop when emptied or idle. */
    fun onDraftChanged(itemId: String, itemName: String, text: String) {
        if (text.isBlank()) { stopTyping(); return }
        val (hid, uid, _) = ctx() ?: return
        val now = System.currentTimeMillis()
        if (app.necessityq.domain.Typing.shouldAnnounce(typingItem, typingAnnouncedAt, itemId, now)) {
            typingItem = itemId
            typingAnnouncedAt = now
            val state = app.necessityq.domain.TypingState(uid, authorName(uid), itemId, itemName, now)
            viewModelScope.launch { runCatching { container.collab.setTyping(hid, uid, state) } }
        }
        typingIdle?.cancel()
        typingIdle = viewModelScope.launch {
            kotlinx.coroutines.delay(app.necessityq.domain.Typing.IDLE_MS)
            stopTyping()
        }
    }

    /** Sent, emptied, idle or the conversation was closed. */
    fun stopTyping() {
        typingIdle?.cancel()
        typingIdle = null
        if (typingItem == null) return
        typingItem = null
        typingAnnouncedAt = 0L
        val (hid, uid, _) = ctx() ?: return
        viewModelScope.launch { runCatching { container.collab.setTyping(hid, uid, null) } }
    }

    /** The view model is going away: clear our mark so others do not see a stale "typing…" (it also expires by itself). */
    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    private fun clearTypingOnExit() {
        if (typingItem == null) return
        val (hid, uid, _) = ctx() ?: return
        kotlinx.coroutines.GlobalScope.launch { runCatching { container.collab.setTyping(hid, uid, null) } }
    }

    fun sendMessage(itemId: String, itemName: String, text: String, replyToId: String? = null) {
        val (hid, uid, _) = ctx() ?: return
        val clean = text.trim()
        if (clean.isEmpty()) return
        stopTyping()
        val message = ItemMessage(
            java.util.UUID.randomUUID().toString(), itemId, itemName, uid, authorName(uid), clean,
            System.currentTimeMillis(), replyToId = replyToId, mentions = mentionsIn(clean, uid),
        )
        if (message.mentions.isNotEmpty()) askNotificationsInContext()
        launchSafe {
            container.collab.sendMessage(hid, message)
            pushAlerts(hid, message)
        }
    }

    /** Edits the text of an own message; only within [app.necessityq.domain.MessageRules.EDIT_WINDOW_MS] of writing it. */
    fun editMessage(message: ItemMessage, text: String) {
        val (hid, uid, _) = ctx() ?: return
        val clean = text.trim()
        if (clean == message.text) return
        if (message.kind == app.necessityq.domain.MessageKind.TEXT && clean.isEmpty()) return
        val now = System.currentTimeMillis()
        if (!app.necessityq.domain.MessageRules.canEdit(message, uid, now)) {
            _messages.tryEmit("אפשר לערוך הודעה רק עד 15 דקות מהכתיבה"); return
        }
        val mentions = mentionsIn(clean, uid)
        launchSafe {
            container.collab.editMessage(hid, message.id, clean, now, mentions)
            // a tag added by the edit is alerted too (the server skips whoever was alerted already)
            if (!message.mentions.containsAll(mentions)) pushAlerts(hid, message.copy(text = clean, mentions = mentions))
        }
    }

    fun deleteMessage(message: ItemMessage) {
        val (hid, uid, _) = ctx() ?: return
        if (!app.necessityq.domain.MessageRules.canDelete(message, uid)) return
        // as in WhatsApp: the message stays in place as "ההודעה נמחקה על ידי …"; its content and attachment are removed
        launchSafe { container.collab.markDeleted(hid, message, System.currentTimeMillis()) }
    }

    /** My emoji reaction to a message; null takes it back. */
    fun react(message: ItemMessage, emoji: String?) {
        val (hid, uid, _) = ctx() ?: return
        if (message.isDeleted) return
        launchSafe { container.collab.react(hid, message.id, uid, emoji) }
    }

    /** Deletes a whole conversation (the open one of an item, or an archived one). */
    fun deleteConversation(messages: List<ItemMessage>) {
        val (hid, _, _) = ctx() ?: return
        if (messages.isEmpty()) return
        launchSafe {
            container.collab.deleteMessages(hid, messages)
            _messages.tryEmit("השיחה נמחקה")
        }
    }

    /** Who received / read a message, and when. */
    fun receiptDetails(m: ItemMessage): List<app.necessityq.domain.MemberReceipt> =
        app.necessityq.domain.ReceiptDetails.of(m, memberNames.value.keys, receipts.value)

    /**
     * The shopping event of these items ended (lines cleared or list reset): their conversations leave
     * the list and wait out the retention period. Items still open on the list keep their conversation.
     */
    private fun archiveChats(itemIds: Collection<String>, remainingOpen: List<ListLine>): (() -> Unit)? {
        val (hid, _, _) = ctx() ?: return null
        val open = remainingOpen.filter { it.status == LineStatus.NEEDED }.map { it.itemId }.toSet()
        val withChat = itemMessages.value.map { it.itemId }.toSet()
        val targets = itemIds.toSet().filter { it !in open && it in withChat }
        if (targets.isEmpty()) return null
        val at = System.currentTimeMillis()
        launchSafe { targets.forEach { container.collab.archiveConversation(hid, it, at) } }
        return { launchSafe { targets.forEach { container.collab.unarchiveConversation(hid, it, at) } } }
    }

    /** Sends a picture or a recording: the bytes first, then the message that points at them. */
    fun sendMedia(
        itemId: String,
        itemName: String,
        kind: app.necessityq.domain.MessageKind,
        media: app.necessityq.domain.MediaBlob,
        caption: String = "",
        durationMs: Long? = null,
        replyToId: String? = null,
        waveform: List<Int>? = null,
    ) {
        val (hid, uid, _) = ctx() ?: return
        if (media.bytes.size > 900_000) { _messages.tryEmit("הקובץ גדול מדי לשליחה"); return }
        val mediaId = java.util.UUID.randomUUID().toString()
        mediaCache[mediaId] = media
        val message = ItemMessage(
            java.util.UUID.randomUUID().toString(), itemId, itemName, uid, authorName(uid), caption.trim(),
            System.currentTimeMillis(), kind = kind, mediaId = mediaId, durationMs = durationMs, replyToId = replyToId, waveform = waveform,
            mentions = mentionsIn(caption, uid),
        )
        if (message.mentions.isNotEmpty()) askNotificationsInContext()
        launchSafe {
            container.collab.saveMedia(hid, mediaId, uid, media)
            container.collab.sendMessage(hid, message)
            pushAlerts(hid, message)
        }
    }

    private val mediaCache = java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, app.necessityq.domain.MediaBlob>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, app.necessityq.domain.MediaBlob>?) = size > 40
        },
    )

    /** Loads a message's picture / recording (cached). */
    suspend fun loadMedia(mediaId: String): app.necessityq.domain.MediaBlob? {
        mediaCache[mediaId]?.let { return it }
        val (hid, _, _) = ctx() ?: return null
        return runCatching { container.collab.loadMedia(hid, mediaId) }.getOrNull()?.also { mediaCache[mediaId] = it }
    }

    val recorder = app.necessityq.platform.VoiceRecorder(container.appContext)
    val player = app.necessityq.platform.VoicePlayer(container.appContext).also { it.setSpeed(prefs.voiceSpeed) }

    /** Speed of voice messages: 1× → 1.5× → 2×, remembered on the phone. */
    val voiceSpeed = MutableStateFlow(prefs.voiceSpeed)
    fun cycleVoiceSpeed() {
        val next = app.necessityq.domain.VoiceSpeed.next(voiceSpeed.value)
        prefs.voiceSpeed = next
        voiceSpeed.value = next
        player.setSpeed(next)
    }

    /** Who is told about new messages on this phone. */
    val chatNotifyMode = MutableStateFlow(prefs.chatNotifyMode)
    fun setChatNotifyMode(mode: app.necessityq.domain.ChatNotifyMode) { prefs.chatNotifyMode = mode; chatNotifyMode.value = mode }

    /** A tag or quote is heard even on silent. */
    val loudMentions = MutableStateFlow(prefs.loudMentions)
    fun setLoudMentions(on: Boolean) { prefs.loudMentions = on; loudMentions.value = on }

    /** A conversation to open (from a tapped notification). */
    val openChatRequest = MutableStateFlow<Pair<String, String>?>(null)
    fun requestOpenChat(itemId: String, itemName: String) { openChatRequest.value = itemId to itemName }
    fun consumeOpenChatRequest() { openChatRequest.value = null }

    override fun onCleared() {
        clearTypingOnExit()
        recorder.stopQuietly()
        player.stop()
    }

    /** Sound for new messages — a quick toggle in the conversation and the messages screen. */
    val chatSound = MutableStateFlow(prefs.chatSound)
    fun setChatSound(on: Boolean) { prefs.chatSound = on; chatSound.value = on }

    fun setMissingRecipients(userIds: Set<String>) {
        val (hid, _, _) = ctx() ?: return
        launchSafe { container.collab.setMissingRecipients(hid, userIds) }
    }

    init { checkStorePrompt() }

    init {
        // צ'אט פוד: this phone receives the signed-in member's tag / quote pushes
        viewModelScope.launch {
            session.map { (it as? Session.SignedIn)?.userId }.distinctUntilChanged().collect { uid ->
                if (uid != null) runCatching { container.chatPush?.register(uid) }
            }
        }
    }

    init {
        // Retention: ended conversations older than the household's setting are deleted; only counts stay.
        viewModelScope.launch {
            combine(archivedMessages, chatRetention) { archived, days ->
                if (days == null) emptyList()
                else app.necessityq.domain.ConversationRetention.expired(archived, days, System.currentTimeMillis())
            }.collect { expired ->
                if (expired.isEmpty()) return@collect
                val (hid, _, _) = ctx() ?: return@collect
                runCatching { container.collab.purgeMessages(hid, expired) }
            }
        }
    }

    private var seenMessages: Set<String>? = null
    private var seenMissing: Set<String>? = null

    init {
        // Local notifications for what other members do while the app is running.
        viewModelScope.launch {
            householdId.flatMapLatest { hid ->
                seenMessages = null
                if (hid == null) emptyFlow() else container.collab.messages(hid)
            }.catch { }.collect { all ->
                // "received" receipt for messages from others
                val newest = all.filter { it.authorId != me && !it.pending }.maxOfOrNull { it.atMillis } ?: 0L
                if (newest > lastSeenReported) {
                    lastSeenReported = newest
                    ctx()?.let { (hid, uid, _) -> launchSafe { container.collab.markSeen(hid, uid, System.currentTimeMillis()) } }
                }
                val seen = seenMessages
                if (seen == null) {
                    seenMessages = all.map { it.id }.toSet()
                } else {
                    val byId = all.associateBy { it.id }
                    all.filter { it.id !in seen && it.authorId != me && !it.pending }.forEach {
                        if (app.necessityq.domain.ChatAlerts.isForMe(it, me, byId)) {
                            // aimed at me: heard even on silent (the push of the same message is ignored)
                            app.necessityq.platform.MentionAlerter.alert(
                                container.appContext, it.id, it.itemId, it.itemName,
                                title = app.necessityq.domain.ChatAlerts.reason(it, me, byId) + " · " + it.itemName,
                                text = it.preview,
                                loud = loudMentions.value,
                            )
                        } else if (chatNotifyMode.value == app.necessityq.domain.ChatNotifyMode.ALL) {
                            container.notifier.notify(
                                it.id.hashCode(), "צ'אט פוד · ${it.itemName}", "${it.authorName}: ${it.preview}", sound = chatSound.value,
                                openItemId = it.itemId, openItemName = it.itemName,
                            )
                        }
                    }
                    seenMessages = all.map { it.id }.toSet()
                }
            }
        }
        viewModelScope.launch {
            snapshot.collect { snap ->
                val lines = snap?.lines ?: return@collect
                val flagged = lines.filter { it.missing && it.status == LineStatus.NEEDED }.associateBy { it.id }
                val previous = seenMissing
                if (previous != null) {
                    val configured = notifySettings.value.missingRecipients
                    // nobody chosen yet = everyone gets notified
                    if (configured.isEmpty() || me in configured) {
                        flagged.values.filter { it.id !in previous && it.missingBy != me }.forEach {
                            val who = it.missingBy?.let { id -> memberNames.value[id] } ?: "מישהו"
                            container.notifier.notify(
                                it.id.hashCode(), "חסר: ${it.displayName}", "$who סימן/ה שהמוצר חסר",
                                openItemId = it.itemId, openItemName = it.displayName,
                            )
                        }
                    }
                }
                seenMissing = flagged.keys
            }
        }
    }

    // ---------- accounts & participants ----------

    val supportsAccounts: Boolean = container.household.supportsAccounts

    val members: StateFlow<List<Member>> = namedMembers
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Notifications are asked for in context, not when the app first opens: when the household is shared with
     * someone else, on the first "חסר", or when tagging someone in צ'אט פוד.
     */
    private val _wantNotifications = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val wantNotifications: SharedFlow<Unit> = _wantNotifications
    private fun askNotificationsInContext() { _wantNotifications.tryEmit(Unit) }

    init {
        viewModelScope.launch {
            members.map { it.size > 1 }.distinctUntilChanged().collect { shared -> if (shared) askNotificationsInContext() }
        }
    }

    fun signInWithGoogle(idToken: String) = launchSafe {
        container.household.signInWithGoogle(idToken)
        _messages.tryEmit("התחברת עם חשבון Google")
    }

    fun signOut() = launchSafe {
        val uid = me
        if (uid.isNotEmpty()) runCatching { container.chatPush?.unregister(uid) }
        container.household.signOut()
    }
    /**
     * The family and the participants are the same people: my account gets a family profile of its own. An unlinked
     * profile with my name is linked to me; otherwise one is made with my name. Done once the family has been read
     * from the household (never on the empty value before it loads, which would overwrite it).
     */
    fun ensureMyFamilyProfile() {
        val uid = me
        if (uid.isEmpty() || ctx() == null) return
        val data = loadedMeals.value ?: return
        if (data.memberFor(uid) != null) return
        val account = members.value.firstOrNull { it.userId == uid } ?: return
        val myName = account.displayName.trim().ifEmpty { return }
        val same = data.members.filter { it.linkedUserId == null && it.name.trim() == myName }
        updateMeals { d ->
            if (d.memberFor(uid) != null) d
            else if (same.size == 1) d.withMember(same.single().copy(linkedUserId = uid))
            else d.withMember(app.necessityq.domain.FamilyMember(id = "account-$uid", name = myName, linkedUserId = uid))
        }
    }

    /** Only my account's name: my family profile was just saved with this name (see MemberScreen). */
    fun syncAccountName(name: String) {
        val clean = name.trim()
        val current = (session.value as? Session.SignedIn)?.account?.displayName
        if (clean.isNotEmpty() && clean != current) launchSafe { container.household.updateDisplayName(clean) }
    }

    /** My account's name; my family profile (when linked) takes the same name, so I am called one thing everywhere. */
    fun updateDisplayName(name: String) {
        launchSafe { container.household.updateDisplayName(name) }
        val clean = name.trim()
        val mine = meals.value.memberFor(me)
        if (clean.isNotEmpty() && mine != null && mine.name != clean) updateMeals { it.withMember(mine.copy(name = clean)) }
    }
    /** [photo]: a small Base64 JPEG (AvatarCodec); null goes back to the Google account's picture. */
    fun updatePhoto(photo: String?) = launchSafe {
        container.household.updatePhoto(photo)
        _messages.tryEmit(if (photo == null) "חזרה לתמונה מחשבון Google" else "התמונה עודכנה")
    }
    fun renameHousehold(name: String) = launchSafe { container.household.renameHousehold(name) }
    fun removeMember(userId: String) = launchSafe {
        container.household.removeMember(userId)
        _messages.tryEmit("המשתתף הוסר מהבית")
    }
    fun regenerateJoinCode() = launchSafe {
        container.household.regenerateJoinCode()
        _messages.tryEmit("נוצר קוד הצטרפות חדש; הקוד הישן אינו תקף עוד")
    }
    fun reportError(message: String) { _messages.tryEmit(message) }

    // ---------- app updates from GitHub Releases ----------

    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update: StateFlow<UpdateState> = _update

    val updater get() = container.updater

    /** Look for a new version on every launch. */
    val autoCheckUpdates = MutableStateFlow(prefs.autoCheckUpdates)
    fun setAutoCheckUpdates(on: Boolean) { prefs.autoCheckUpdates = on; autoCheckUpdates.value = on }

    /** Open the installer by itself once the download finishes. */
    val autoInstallUpdates = MutableStateFlow(prefs.autoInstallUpdates)
    fun setAutoInstallUpdates(on: Boolean) { prefs.autoInstallUpdates = on; autoInstallUpdates.value = on }

    /**
     * A finished download the screen should hand to the installer (auto-install). The activity takes it
     * while it is in the foreground, because Android does not let a backgrounded app open the installer.
     */
    private val _pendingInstall = MutableStateFlow<File?>(null)
    val pendingInstall: StateFlow<File?> = _pendingInstall
    fun consumePendingInstall() { _pendingInstall.value = null }

    private var downloadJob: kotlinx.coroutines.Job? = null

    private var lastUpdateCheck = 0L

    /**
     * Every return to the app checks for an update (when automatic checks are on), at most every
     * 15 minutes. The first call comes from the activity's start, so this also covers opening the app.
     */
    private var presenceJob: kotlinx.coroutines.Job? = null

    /** While the app is in front, a heartbeat every minute tells the household this member is here. */
    private fun startPresence() {
        presenceJob?.cancel()
        presenceJob = viewModelScope.launch {
            while (true) {
                runCatching { container.household.setPresence(true) }
                kotlinx.coroutines.delay(60_000L)
            }
        }
    }

    /** The app left the front: the others see this member as gone at once. */
    fun onBackground() {
        presenceJob?.cancel()
        presenceJob = null
        // not in viewModelScope's cancelled work: a short one-off write
        viewModelScope.launch { runCatching { container.household.setPresence(false) } }
    }

    fun onForeground() {
        startPresence()
        // back in the app: promotions and stock are checked again when the last check is half an hour old
        refreshOffers()
        // standing in a followed branch while preparing the list? (offers to switch to shopping)
        if (mode.value == ListMode.PLAN) locate()
        if (!prefs.autoCheckUpdates) return
        if (System.currentTimeMillis() - lastUpdateCheck < 15 * 60_000L) return
        when (_update.value) {
            UpdateState.Idle, UpdateState.UpToDate, is UpdateState.Failed -> checkForUpdates(manual = false)
            else -> Unit // a found / downloading / downloaded update stays as it is
        }
    }

    fun checkForUpdates(manual: Boolean) {
        if (_update.value is UpdateState.Checking || _update.value is UpdateState.Downloading) return
        lastUpdateCheck = System.currentTimeMillis()
        _update.value = UpdateState.Checking
        viewModelScope.launch {
            runCatching { container.updater.latest() }
                .onSuccess { release ->
                    _update.value = when {
                        release == null -> UpdateState.UpToDate
                        Releases.isNewer(release.versionCode, BuildConfig.VERSION_CODE) -> {
                            // one tap on "עדכון" downloads and installs, without going to "עדכונים"
                            if (!manual) _undo.tryEmit(UndoEvent("יצאה גרסה חדשה ${release.versionName}", label = "עדכון") { updateNow() })
                            UpdateState.Available(release)
                        }
                        else -> UpdateState.UpToDate
                    }
                }
                .onFailure {
                    // a silent startup check must not nag when offline
                    _update.value = if (manual) UpdateState.Failed(it.message ?: "בדיקת העדכון נכשלה") else UpdateState.Idle
                }
        }
    }

    /**
     * Downloads the update in the background: it keeps going while the user moves around the app or
     * leaves it, with an ongoing notification showing the progress. [stopDownload] cancels it.
     */
    fun downloadUpdate() {
        val release = when (val s = _update.value) {
            is UpdateState.Available -> s.release
            is UpdateState.Stopped -> s.release
            else -> return
        }
        _update.value = UpdateState.Downloading(release, 0f)
        container.notifier.updateProgress(release.versionName, null)
        downloadJob = viewModelScope.launch {
            var shownPercent = -1
            try {
                val file = container.updater.download(release) { p ->
                    _update.value = UpdateState.Downloading(release, p)
                    val percent = (p * 100).toInt()
                    if (percent != shownPercent) { // one notification update per percent, not per chunk
                        shownPercent = percent
                        container.notifier.updateProgress(release.versionName, percent)
                    }
                }
                _update.value = UpdateState.Ready(release, file)
                container.notifier.updateReady(release.versionName, container.updater.installIntent(file))
                if (prefs.autoInstallUpdates || installWhenReady) _pendingInstall.value = file
                installWhenReady = false
            } catch (e: kotlinx.coroutines.CancellationException) {
                container.notifier.cancelUpdate()
                throw e
            } catch (e: Exception) {
                container.notifier.cancelUpdate()
                _update.value = UpdateState.Failed(e.message ?: "ההורדה נכשלה")
            }
        }
    }

    /** "עדכון" was tapped on the new-version message: the installer opens as soon as the download ends. */
    private var installWhenReady = false

    /**
     * "עדכון" on the new-version message: downloads the update and opens the installer when it is in (or at once,
     * when it was already downloaded) — no need to go to "עדכונים" and tap "התקנה".
     */
    fun updateNow() {
        when (val s = _update.value) {
            is UpdateState.Ready -> _pendingInstall.value = s.file
            is UpdateState.Downloading -> installWhenReady = true
            is UpdateState.Available, is UpdateState.Stopped -> { installWhenReady = true; downloadUpdate() }
            else -> Unit
        }
    }

    /** Stops a running download; the partial file is deleted and the update can be downloaded again. */
    fun stopDownload() {
        val downloading = _update.value as? UpdateState.Downloading ?: return
        downloadJob?.cancel()
        downloadJob = null
        container.notifier.cancelUpdate()
        _update.value = UpdateState.Stopped(downloading.release)
    }

    fun createHousehold(name: String) = launchSafe { container.household.createHousehold(name) }
    fun joinHousehold(code: String) = launchSafe { container.household.joinHousehold(code) }

    /** A household code from a join link, waiting for the user's one tap (or for the explanation, when already in one). */
    val pendingJoin = MutableStateFlow<String?>(null)
    fun requestJoin(code: String) { pendingJoin.value = code }
    fun consumeJoin() { pendingJoin.value = null }
    fun leaveHousehold() = launchSafe { container.household.leaveHousehold() }

    /** The stages of the opening load, in order. */
    enum class StartupStep { ACCOUNT, LIST, PRICES, READY }

    companion object {
        const val MAX_BRANCHES = 5
        /** Promotions and stock are checked every half hour (and on every opening). */
        const val OFFERS_MINUTES = 30
        /** Pause in typing before a search runs. */
        const val SEARCH_DELAY_MS = 350L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { AppViewModel(container) }
        }
    }
}

private data class OnlineSearch(val query: String, val mine: List<ItemIdentity>, val products: List<app.necessityq.domain.BranchProduct>?)
private data class PriceInputs(
    val snapshot: ListSnapshot?,
    val loaded: Map<String, app.necessityq.domain.BranchPrices>,
    val primary: String?,
    val preferred: Map<String, String>,
)
private data class BranchInfoInputs(
    val lines: List<ListLine>,
    val loaded: Map<String, app.necessityq.domain.BranchPrices>,
    val followed: List<String>,
    val preferred: Map<String, String>,
)
private data class TripInputs(
    val infos: Map<String, Map<String, app.necessityq.domain.ItemPriceInfo>>,
    val promos: Map<String, app.necessityq.domain.BranchPromotions>,
    val settings: AppViewModel.TripSettings,
    val followed: List<String>,
)
private data class PromoInputs(
    val infos: Map<String, Map<String, app.necessityq.domain.ItemPriceInfo>>,
    val promos: Map<String, app.necessityq.domain.BranchPromotions>,
    val members: Boolean,
    val tried: Set<String>,
)

private data class PriceReady(val branchId: String?, val loaded: Boolean, val tried: Boolean, val pricedFor: String?)
private data class CompareInputs(
    val infos: Map<String, app.necessityq.domain.ItemPriceInfo>,
    val loaded: Map<String, app.necessityq.domain.BranchPrices>,
    val followed: List<String>,
    val preferred: Map<String, String>,
)

/** State of the in-app update flow. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: app.necessityq.domain.ReleaseInfo) : UpdateState
    data class Downloading(val release: app.necessityq.domain.ReleaseInfo, val progress: Float) : UpdateState
    /** The user stopped the download; it can be started again. */
    data class Stopped(val release: app.necessityq.domain.ReleaseInfo) : UpdateState
    data class Ready(val release: app.necessityq.domain.ReleaseInfo, val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}
