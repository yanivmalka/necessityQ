package app.necessityq.domain

/** Where a help entry can take the user: the screen (or the settings tab) that holds the thing it explains. */
enum class HelpTarget(val label: String) {
    SETTINGS_FEATURES("פתיחת ההגדרות: יכולות"),
    SETTINGS_LIST("פתיחת ההגדרות: הרשימה"),
    SETTINGS_LOOK("פתיחת ההגדרות: מראה"),
    SETTINGS_NOTIFY("פתיחת ההגדרות: התראות"),
    PRICES("מעבר ללשונית מחירים"),
    MENU("מעבר ללשונית תפריט"),
    LIST("מעבר לרשימה"),
    HOUSEHOLD("מעבר לבני הבית ושיתוף"),
    ACCOUNT("מעבר לחשבון"),
    MESSAGES("מעבר לצ'אט פוד"),
    HISTORY("מעבר להיסטוריה"),
    UPDATES("מעבר לעדכונים"),
}

/**
 * One explained thing: what it is, what it gives, what it costs, and what to do — written in words, with no arrows
 * (a Hebrew line that mixes arrows and digits jumbles when it is drawn right-to-left). [keywords] are the other
 * names people use for it, which the search also finds.
 */
data class HelpEntry(
    val id: String,
    val title: String,
    val what: String,
    val benefit: String? = null,
    val drawback: String? = null,
    /** The steps, in words ("בהגדרות, בלשונית יכולות, מדליקים את המתג"). */
    val howTo: String,
    val keywords: List<String> = emptyList(),
    val target: HelpTarget? = null,
)

/** A common trouble: what the user sees, why, and the steps that fix it. */
data class HelpProblem(
    val id: String,
    val title: String,
    val why: String,
    val steps: List<String>,
    val target: HelpTarget? = null,
)

/**
 * The content of the help page: the seven common problems, and the entries the search looks in (every feature of
 * "יכולות", the settings, and the main gestures). The search is forgiving: it ignores question words ("איך",
 * "מה זה", "מדליקים"), prefixes like "ה", plurals, geresh and spacing.
 */
object HelpCatalog {

    val problems: List<HelpProblem> = listOf(
        HelpProblem(
            "no-prices", "לא רואים מחירים על הפריטים",
            "המחירים מגיעים מהסופרים שהבית בחר. בלי סופר במעקב, או כשהיכולת כבויה, אין מה להציג.",
            listOf(
                "נכנסים ללשונית מחירים ובוחרים סופר אחד לפחות (אפשר לפי המיקום).",
                "בהגדרות, בלשונית יכולות, בודקים שהמתג \"מחירים על האריחים וסכום משוער\" דלוק.",
                "בהגדרות, בלשונית הרשימה, ב\"מה מוצג על כל פריט\", בודקים שהמחיר לא הוסתר.",
                "מוצר שאין לו התאמה בסופר יופיע עם קו במקום מחיר. אפשר לבחור לו מוצר מתאים בדף הפריט.",
            ),
            HelpTarget.PRICES,
        ),
        HelpProblem(
            "no-promos", "לא רואים מבצעים",
            "מבצעים נטענים בפתיחת האפליקציה, ורק למוצרים שיש עליהם מבצע בסופרים שבמעקב ושהם במלאי.",
            listOf(
                "בודקים שיש סופר במעקב בלשונית מחירים.",
                "בהגדרות, בלשונית יכולות, מדליקים \"מבצעים\".",
                "מבצעי מועדון, כרטיס אשראי וקופון נכללים רק כשמסמנים זאת בתכנון \"איפה הכי משתלם לקנות\".",
                "אם בחרתם \"נגיעה כפולה לפתיחת פריט\", פס המבצע מוסתר: נוגעים בפריט פעם אחת והוא נפתח, ופעמיים נכנסים לפריט.",
            ),
            HelpTarget.SETTINGS_FEATURES,
        ),
        HelpProblem(
            "swipe", "החלקה על פריט מוחקת או מסמנת בטעות",
            "ההחלקה עושה פעולה שונה בהכנת רשימה ובקנייה, וכל צד אפשר להגדיר בנפרד.",
            listOf(
                "מחזיקים את ההחלקה עד שהטבעת מתמלאת: נגיעה קצרה בטעות לא תפעיל כלום.",
                "כל פעולה מציגה הודעה עם \"ביטול\" לחצי דקה. מה שנמחק מחכה גם בסל המחזור, בתפריט הנקודות בראש הרשימה.",
                "בהגדרות, בלשונית הרשימה, בכרטיס \"החלקה על פריט\", בוחרים מה עושה כל צד, לכל שלב.",
                "לא נוח להחליק? בקנייה אפשר לגעת בעיגול שליד הפריט כדי לסמן אותו כנקנה.",
            ),
            HelpTarget.SETTINGS_LIST,
        ),
        HelpProblem(
            "no-notify", "לא מגיעות התראות",
            "ההתראות תלויות בהרשאה של הטלפון, ובצ'אט פוד גם במי שתויג, כדי שלא כולם יקבלו על כל הודעה.",
            listOf(
                "בהגדרות, בלשונית התראות, בכרטיס \"הרשאות\", בודקים שהאפליקציה רשאית לשלוח התראות.",
                "בצ'אט פוד התראה מגיעה למי שתויג ב-@ ולמי שההודעה שלו צוטטה. אפשר לבחור גם \"כל הודעה\".",
                "אם הטלפון על שקט, אפשר להדליק צליל לתיוג וציטוט באותו מקום.",
                "צ'אט פוד כבוי? בלשונית יכולות מדליקים אותו.",
            ),
            HelpTarget.SETTINGS_NOTIFY,
        ),
        HelpProblem(
            "household", "בני הבית לא רואים את הרשימה שלי",
            "הרשימה משותפת רק למי שהצטרף לאותו בית, בקוד או בקישור הזמנה.",
            listOf(
                "נכנסים אל בני הבית ושיתוף, ושולחים לבן או בת הבית את קישור ההזמנה או את הקוד.",
                "מי שפותח את הקישור מצטרף בנגיעה אחת. אפשר גם להקליד את הקוד בעצמם.",
                "מי שכבר יצר בית נפרד צריך לעזוב אותו ולהצטרף לבית שלכם.",
            ),
            HelpTarget.HOUSEHOLD,
        ),
        HelpProblem(
            "running-low", "לא מופיע \"כנראה נגמר בקרוב\"",
            "האפליקציה לומדת מקניות: אחרי כמה פעמים שסימנתם מוצר כנקנה היא יודעת כל כמה זמן הבית קונה אותו.",
            listOf(
                "בהגדרות, בלשונית יכולות, בודקים ש\"כנראה נגמר בקרוב\" דלוק (בשלב הכנת רשימה).",
                "מסמנים מוצרים כנקנו בקנייה: הלמידה מתחילה אחרי כמה קניות של אותו מוצר.",
                "הצעה שהשתקתם חוזרת רק אחרי חצי מהזמן הרגיל של המוצר.",
            ),
            HelpTarget.SETTINGS_FEATURES,
        ),
        HelpProblem(
            "account", "האפליקציה לא יודעת מי אני, או שהבית נעלם",
            "בלי חשבון Google המכשיר פועל כאורח: הבית שמור במכשיר הזה בלבד ויכול ללכת לאיבוד בהחלפת מכשיר או בהתקנה מחדש.",
            listOf(
                "בדף החשבון כתוב אם אתם מחוברים לחשבון Google או אורחים.",
                "אורחים: לוחצים על \"התחברות עם Google\" בדף החשבון, והשם, הכתובת והתמונה מגיעים מהחשבון.",
                "אחרי התנתקות המכשיר ממשיך כאורח חדש. מתחברים שוב עם אותו חשבון כדי לחזור לבית.",
            ),
            HelpTarget.ACCOUNT,
        ),
    )

    /** What the search finds: the features of "יכולות" (built from their own descriptions), then the rest. */
    val entries: List<HelpEntry> by lazy { featureEntries() + generalEntries }

    private fun featureEntries(): List<HelpEntry> = Feature.entries.map { f ->
        val stage = when (f.scope) {
            FeatureScope.APP -> "מתג אחד לכל האפליקציה."
            FeatureScope.PLAN -> "פועלת בשלב הכנת הרשימה: בוחרים אותו בראש דף היכולות."
            FeatureScope.SHOP -> "פועלת בקנייה: בוחרים \"בקנייה\" בראש דף היכולות."
            FeatureScope.BOTH -> "לכל שלב יש מתג משלו: בוחרים הכנת רשימה או בקנייה בראש דף היכולות."
        }
        HelpEntry(
            id = "feature-" + f.name.lowercase(),
            title = f.label.replace("\"", ""),
            what = f.description,
            benefit = f.group.hint,
            drawback = "כיבוי רק מסתיר: האפליקציה ממשיכה ללמוד ברקע, כך שכשמדליקים שוב הכול מוכן. " +
                "יכולת דלוקה מוסיפה לרשימה עוד רכיבים, ולכן אפשר לכבות מה שלא עוזר.",
            howTo = "בהגדרות, בלשונית יכולות, בכרטיס \"${f.group.label}\", מדליקים את המתג ליד \"${f.label.replace("\"", "")}\". $stage",
            keywords = featureKeywords[f].orEmpty(),
            target = HelpTarget.SETTINGS_FEATURES,
        )
    }

    private val featureKeywords: Map<Feature, List<String>> = mapOf(
        Feature.MENU_TAB to listOf("ארוחות", "מתכונים", "תפריט שבועי", "משפחה", "לשונית"),
        Feature.PRICES_TAB to listOf("סופרים", "סופר", "חיפוש מוצר", "השוואת מחירים", "לשונית"),
        Feature.CHAT to listOf("שיחה", "הודעות", "צאט", "צ'אט", "תיוג", "הקלטה", "תמונה"),
        Feature.RUNNING_LOW to listOf("נגמר", "הצעות", "תזכורת", "חסר", "למידה"),
        Feature.FREQUENT to listOf("קונים תמיד", "הכי הרבה", "הוספה מהירה", "תדירות"),
        Feature.TRIP_PLAN to listOf("איפה הכי משתלם", "פיצול", "כמה סופרים", "חיסכון", "קופון", "מועדון"),
        Feature.PRICES to listOf("מחיר", "עלות", "סכום", "כמה עולה", "סל"),
        Feature.PROMOS to listOf("מבצע", "הנחה", "מתנה", "מלאי", "קופון"),
        Feature.BRANCH_COMPARE to listOf("יקר", "זול", "השוואה", "חץ"),
        Feature.RECIPES_ON_LIST to listOf("מתכון", "מנות", "מצרכים"),
        Feature.TILE_EXTRAS to listOf("גרירה", "ידית", "סטטיסטיקה", "סידור ידני"),
    )

    private val generalEntries: List<HelpEntry> = listOf(
        HelpEntry(
            "two-stages", "שני שלבים: הכנת רשימה ובקנייה",
            "המתג בראש הרשימה עובר בין הכנת רשימה, בבית, שבה מוסיפים ומתכננים, ובקנייה, בסופר, שבה מסמנים מה נכנס לעגלה.",
            "כל שלב מראה רק מה שמועיל לו, וליכולות רבות יש מתג נפרד לכל שלב.",
            "\"יוצאים לקנות\" בתחתית הרשימה עובר לקנייה, ורק \"סיום קנייה\" מסיים אותה: מה שנקנה עובר לסל המחזור, ואפשר לבטל.",
            "בראש הרשימה לוחצים על המתג. בתחתית הרשימה לוחצים על \"יוצאים לקנות\".",
            listOf("בחנות", "בסופר", "בבית", "יוצאים לקנות", "סיום קנייה", "מצב קנייה"),
            HelpTarget.LIST,
        ),
        HelpEntry(
            "add-items", "הוספת מוצרים",
            "כותבים בשורה שבראש הרשימה כמו שמדברים: \"2 קוטג' 5%\", \"חצי קילו גבינה צהובה\", \"חלב 3% 2 ליטר\".",
            "הכמות והמוצר מזוהים לבד. מוצר שכבר ברשימה לא מוכפל: הכמות מתווספת לו.",
            null,
            "כותבים בשורה שבראש הרשימה ולוחצים על הפלוס. סמל הברקוד בשורה סורק מוצר ומוסיף אותו.",
            listOf("להוסיף", "הוספה", "ברקוד", "סריקה", "כתיבה", "כמות"),
            HelpTarget.LIST,
        ),
        HelpEntry(
            "tap-mode", "נגיעה בפריט: פעם אחת או פעמיים",
            "בוחרים אם נגיעה אחת בפריט פותחת את דף הפריט, או שנדרשות שתי נגיעות.",
            "שתי נגיעות מונעות פתיחה בטעות בזמן גלילה. עם שתי נגיעות, נגיעה אחת בפריט שיש עליו מבצע פותחת את פס המבצע שמתחתיו.",
            "פתיחת פריט לוקחת נגיעה נוספת.",
            "בהגדרות, בלשונית הרשימה, בכרטיס \"נגיעה בפריט\", בוחרים נגיעה אחת או נגיעה כפולה.",
            listOf("לחיצה", "לחיצה כפולה", "פתיחת פריט", "דף הפריט", "מבצע מתחת לפריט"),
            HelpTarget.SETTINGS_LIST,
        ),
        HelpEntry(
            "tile-content", "מה מוצג על כל פריט",
            "בוחרים אילו רכיבים מופיעים על כל פריט: מחיר, כמות וכפתורי פלוס ומינוס, משקל משוער, עיגול סימון ועוד, לכל שלב בנפרד.",
            "רשימה נקייה יותר, או מידע רב יותר, לפי מה שנוח לכם.",
            "רכיב שהיכולת שלו כבויה מופיע כמוסתר, והצגתו מדליקה את היכולת.",
            "בהגדרות, בלשונית הרשימה, בכרטיס \"מה מוצג על כל פריט ברשימה\", בוחרים שלב ולוחצים על עריכה. נגיעה ברכיב בפריט לדוגמה מסתירה או מחזירה אותו.",
            listOf("אריח", "הסתרת מחיר", "כפתורי כמות", "תצוגה", "עריכת פריט"),
            HelpTarget.SETTINGS_LIST,
        ),
        HelpEntry(
            "swipe-settings", "החלקה על פריט",
            "מה קורה כשמושכים פריט שמאלה או ימינה. בהכנת רשימה ההחלקה מסירה את הפריט או לא עושה דבר. בקנייה היא יכולה לסמן כנקנה, לסמן כחסר, או לפתוח את צ'אט פוד של המוצר.",
            "פעולה מהירה בלי לפתוח כלום, ואפשר לבחור לכל צד.",
            "החלקה בטעות עלולה לסמן. לכן מחזיקים עד שהטבעת מתמלאת, ויש ביטול לחצי דקה.",
            "בהגדרות, בלשונית הרשימה, בכרטיס \"החלקה על פריט\", בוחרים לכל צד ולכל שלב את הפעולה.",
            listOf("החלקה", "גרירה", "סימון נקנה", "חסר", "מחיקה", "ביטול"),
            HelpTarget.SETTINGS_LIST,
        ),
        HelpEntry(
            "undo-recycle", "ביטול וסל מחזור",
            "כל פעולה ברשימה מציגה הודעה עם \"ביטול\" לחצי דקה. מה שנמחק או נקנה מחכה בסל המחזור.",
            "אפשר לתקן טעות בלי לכתוב הכול מחדש.",
            null,
            "בראש הרשימה, בתפריט הנקודות, פותחים את סל המחזור ומחזירים מוצר. את ההיסטוריה של הבית אפשר לראות מדף הבית.",
            listOf("סל המחזור", "מחזור", "לשחזר", "מחקתי בטעות", "היסטוריה", "אופס"),
            HelpTarget.HISTORY,
        ),
        HelpEntry(
            "list-order", "סדר הרשימה",
            "מוצרי קירור והקפאה אפשר להעביר לסוף הרשימה, ובקנייה אפשר להציג מה שכבר נקנה בתחתית, מחוק בקו.",
            "הקניות בסופר נעשות לפי סדר מעבר נוח, וקל לראות מה כבר נקנה.",
            null,
            "בהגדרות, בלשונית הרשימה, בכרטיס \"סדר הרשימה\", מדליקים את המתגים.",
            listOf("קירור", "הקפאה", "מיון", "סינון", "קטגוריות", "מה שנקנה"),
            HelpTarget.SETTINGS_LIST,
        ),
        HelpEntry(
            "branches", "בחירת סופרים",
            "הסופרים שהבית קונה בהם. המחירים והמבצעים נטענים מהם, פעם ביום וגם בכניסה למצב קנייה.",
            "מחיר ומבצע על כל מוצר, וסכום משוער לרשימה.",
            "מספר הסופרים במעקב מוגבל, וצריך אינטרנט לרענון.",
            "בלשונית מחירים, בסופרים קרובים, מסמנים את הסופרים (אפשר לפי המיקום שלכם או לפי חיפוש).",
            listOf("סופר", "חנות", "שופרסל", "רמי לוי", "מיקום", "מעקב אחרי סופר"),
            HelpTarget.PRICES,
        ),
        HelpEntry(
            "product-search", "חיפוש מוצר ומחיר",
            "חיפוש לפי שם או ברקוד בכל הסופרים שבמעקב, עם המחיר בכל סופר והזול מסומן.",
            "רואים איפה כדאי לקנות, ומוסיפים לרשימה ישירות מהתוצאה.",
            null,
            "בלשונית מחירים, במוצרים ומחירים, נוגעים בשדה החיפוש. תוצאות החיפוש נגללות, והמקלדת נסגרת כשמתחילים לגלול.",
            listOf("חיפוש", "ברקוד", "השוואה", "זול"),
            HelpTarget.PRICES,
        ),
        HelpEntry(
            "colors-icon", "צבעים וסמל האפליקציה",
            "בוחרים את צבע האפליקציה ואת הסמל שלה במסך הבית של הטלפון.",
            "מתאימים את המראה לטעם שלכם.",
            null,
            "בהגדרות, בלשונית מראה, בכרטיס \"צבעים וסמל\".",
            listOf("צבע", "ערכת נושא", "מראה", "מצב כהה", "אייקון", "סמל"),
            HelpTarget.SETTINGS_LOOK,
        ),
        HelpEntry(
            "tab-order", "סדר הלשוניות",
            "בוחרים באיזה סדר מופיעות הלשוניות בסרגל התחתון, ובאיזו לשונית נפתחת האפליקציה.",
            "הדבר שאתם משתמשים בו הכי הרבה תמיד קרוב ליד.",
            null,
            "בהגדרות, בלשונית מראה, בכרטיס \"סדר הלשוניות\".",
            listOf("סרגל תחתון", "לשונית פתיחה", "סדר"),
            HelpTarget.SETTINGS_LOOK,
        ),
        HelpEntry(
            "notifications", "התראות והרשאות",
            "מה האפליקציה רשאית לעשות בטלפון, ומתי היא שולחת התראה: בצ'אט פוד רק למי שתויג ב-@ או שההודעה שלו צוטטה, או על כל הודעה.",
            "מקבלים את מה שמעניין בלי הצפה.",
            "כיבוי הרשאה נעשה בהגדרות הטלפון, שנפתחות בנגיעה.",
            "בהגדרות, בלשונית התראות, בוחרים את מצב ההתראה ובודקים את כרטיס \"הרשאות\".",
            listOf("התראה", "צליל", "שקט", "הרשאה", "תיוג", "מיקום", "מצלמה"),
            HelpTarget.SETTINGS_NOTIFY,
        ),
        HelpEntry(
            "household-share", "שיתוף עם בני הבית",
            "כל בני הבית רואים את אותה רשימה בזמן אמת: מי הוסיף, מי סימן, ומה חסר.",
            "אחד קונה, האחרים מוסיפים מהבית, ואין כפילויות.",
            null,
            "בדף הבית נכנסים אל בני הבית ושיתוף, ושולחים את קישור ההזמנה או את הקוד.",
            listOf("שיתוף", "הזמנה", "קוד", "קישור", "להצטרף", "בני משפחה", "עוד אדם"),
            HelpTarget.HOUSEHOLD,
        ),
        HelpEntry(
            "online-dot", "נקודה ירוקה: מי פעיל עכשיו",
            "נקודה ירוקה על התמונה של בן או בת בית אומרת שהם נמצאים באפליקציה ברגע זה. כשהם יוצאים ממנה, או כשהמסך נכבה, הנקודה נעלמת תוך רגע.",
            "רואים מי בבית מעדכן את הרשימה עכשיו, ואפשר לתאם קנייה בלי לשאול.",
            "הנקודה מתעדכנת פעם בדקה בערך, ודורשת אינטרנט. מי שאין לו חיבור נראה כלא פעיל.",
            "הנקודה מופיעה בדף הבית ליד תמונות בני הבית, וברשימת המשתתפים בדף בני הבית ושיתוף. אין צורך להדליק דבר.",
            listOf("מחובר", "אונליין", "פעיל", "נקודה", "נוכחות", "מי באפליקציה", "ירוק"),
            HelpTarget.HOUSEHOLD,
        ),
        HelpEntry(
            "account-google", "חשבון Google ואורח",
            "בלי חשבון Google המכשיר פועל כאורח. עם חשבון, השם, הכתובת והתמונה מגיעים ממנו.",
            "חשבון שומר את הבית כשמחליפים מכשיר או מתקינים מחדש.",
            "אורח שמתקין מחדש עלול לאבד את הבית.",
            "בדף הבית, בחשבון, לוחצים על \"התחברות עם Google\". אפשר לשנות שם ותמונה בלי לגעת בחשבון Google.",
            listOf("התחברות", "גוגל", "אורח", "פרופיל", "התנתקות", "תמונה", "חשבון"),
            HelpTarget.ACCOUNT,
        ),
        HelpEntry(
            "learning", "איך האפליקציה לומדת",
            "כל סימון \"נקנה\" נרשם. אחרי כמה קניות האפליקציה יודעת כל כמה זמן הבית קונה כל מוצר ובאיזו כמות.",
            "הצעות מדויקות, ועם הסבר: \"בדרך כלל כל 5 ימים, נקנה לפני 4 ימים\".",
            "בלי קניות מסומנות אין ממה ללמוד.",
            "מסמנים מוצרים כנקנו בשלב הקנייה. הלמידה ממשיכה גם כשההצעות כבויות.",
            listOf("למידה", "הצעות", "קצב", "תדירות"),
            HelpTarget.SETTINGS_FEATURES,
        ),
        HelpEntry(
            "advisor", "הצעות מהאפליקציה",
            "מדי פעם, לכל היותר פעם בשבוע, מופיע מעל הרשימה כרטיס שמציע יכולת שכנראה תעזור לכם.",
            "מגלים יכולות בלי לחפש אותן בהגדרות.",
            null,
            "\"לא תודה\" לא תציע שוב. אפשר לחזור להצעות שדחיתם דרך דף הבית.",
            listOf("הצעה", "דחיתי", "לא תודה", "כרטיס"),
            HelpTarget.SETTINGS_FEATURES,
        ),
        HelpEntry(
            "updates", "עדכוני האפליקציה",
            "האפליקציה בודקת אם יצאה גרסה חדשה ומאפשרת להוריד ולהתקין אותה.",
            "תיקונים ויכולות חדשות בלי להסתבך.",
            null,
            "בדף הבית, בעדכונים, בודקים, מורידים ומתקינים. שם אפשר להפעיל בדיקה והתקנה אוטומטיות.",
            listOf("גרסה", "עדכון", "התקנה"),
            HelpTarget.UPDATES,
        ),
    )

    // ---------- search ----------

    private val stopWords = setOf(
        "איך", "מה", "זה", "זאת", "את", "של", "לי", "אני", "למה", "איפה", "מתי", "אפשר", "צריך", "צריכה", "רוצה",
        "מדליקים", "מדליק", "להדליק", "מכבים", "מכבה", "לכבות", "עושים", "עושה", "לעשות", "פותחים", "לפתוח", "מוצאים",
        "למצוא", "יש", "אין", "על", "עם", "או", "גם", "הוא", "היא", "בעיה", "לא", "כדי", "מי", "כל", "אם",
    )

    private val punctuation = Regex("[^\\p{L}\\p{N}% ]")

    private fun tokens(query: String): List<String> =
        HebrewText.matchKey(query).replace(punctuation, " ").split(' ').filter { it.isNotBlank() && it !in stopWords }

    /** A word in the forms it may be written in: as is, without a one-letter prefix, without a plural ending. */
    private fun forms(word: String): List<String> {
        val out = linkedSetOf(word)
        val noPrefix = if (word.length > 3 && word[0] in "הבלוכמש") word.drop(1) else word
        out += noPrefix
        for (w in listOf(word, noPrefix)) {
            if (w.length > 4 && (w.endsWith("ים") || w.endsWith("ות"))) out += w.dropLast(2)
        }
        return out.filter { it.length >= 2 }
    }

    private fun score(tokens: List<String>, title: String, keywords: List<String>, body: String): Int {
        val t = HebrewText.matchKey(title)
        val k = keywords.joinToString(" ") { HebrewText.matchKey(it) }
        val b = HebrewText.matchKey(body)
        var total = 0
        for (token in tokens) {
            val options = forms(token)
            // a one-letter word has no usable form (forms() drops anything shorter than 2), so it finds nothing
            val best = options.maxOfOrNull { f ->
                when {
                    t.contains(f) -> 6
                    k.contains(f) -> 4
                    b.contains(f) -> 1
                    else -> 0
                }
            } ?: 0
            if (best == 0) return 0 // every word of the question must be found somewhere in the entry
            total += best
        }
        return total
    }

    /** Problems that answer [query], best first; all of them when there is no question. */
    fun searchProblems(query: String): List<HelpProblem> {
        val q = tokens(query)
        if (q.isEmpty()) return if (query.isBlank()) problems else emptyList()
        return problems
            .map { it to score(q, it.title, emptyList(), it.why + " " + it.steps.joinToString(" ")) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    /** Entries that answer [query], best first (title, then other names, then the text). */
    fun search(query: String): List<HelpEntry> {
        val q = tokens(query)
        if (q.isEmpty()) return emptyList()
        return entries
            .map { it to score(q, it.title, it.keywords, listOfNotNull(it.what, it.benefit, it.drawback, it.howTo).joinToString(" ")) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
    }
}
