package app.necessityq.domain

/**
 * The emoji offered by the "+" of the reaction bar, grouped as in WhatsApp's picker. A curated set that
 * suits a family shopping app (food first after the faces), each emoji written once, in display order.
 */
object EmojiCatalog {
    class Category(val name: String, val icon: String, val emojis: List<String>)

    private fun list(s: String) = s.trim().split(Regex("\\s+"))

    val categories: List<Category> = listOf(
        Category(
            "פרצופים", "😀",
            list(
                """
                😀 😃 😄 😁 😆 😅 🤣 😂 🙂 🙃 😉 😊 😇 🥰 😍 🤩 😋 😛 😜 🤪 😝 🤑 🤗 🤭 🤫 🤔 🤐 🤨 😐 😑 😶 😏 😒 🙄 😬
                😌 😔 😪 🤤 😴 😷 🤒 🤕 🤢 🤮 🤧 🥵 🥶 🥴 😵 🤯 🤠 🥳 🥸 😎 🤓 🧐 😕 😟 🙁 😮 😯 😲 😳 🥺 😦 😧 😨 😰 😥
                😢 😭 😱 😖 😣 😞 😓 😩 😫 🥱 😤 😡 😠 🤬 💀 🤡 👻 👽 🤖
                """,
            ),
        ),
        Category(
            "ידיים", "👍",
            list("👍 👎 👌 🤌 🤏 ✌️ 🤞 🤟 🤙 👈 👉 👆 👇 ☝️ ✋ 🤚 🖐️ 🖖 👋 👏 🙌 👐 🤲 🤝 🙏 ✍️ 💪"),
        ),
        Category(
            "לבבות וסמלים", "❤️",
            list(
                """
                ❤️ 🧡 💛 💚 💙 💜 🤎 🖤 🤍 💔 ❣️ 💕 💞 💓 💗 💖 💝 💯 💢 💥 💫 💬 💭 💤 ✅ ☑️ ❌ ❓ ❗ ‼️ ⭐ 🌟 ✨ 🔥
                🎉 🎊 🎈 🏆 🥇 ✡️ 🕎 🕍 📜 🆕 🆗 🆒 ➕ ➖ ✖️ 🔝 🔜 ⚠️ 🚫
                """,
            ),
        ),
        Category(
            "אוכל", "🍎",
            list(
                """
                🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🫐 🍈 🍒 🥭 🍍 🥥 🥝 🍅 🥑 🥦 🥬 🥒 🌶️ 🫑 🌽 🥕 🧄 🧅 🥔 🍠 🥐 🥯 🍞 🥖 🥨
                🧀 🥚 🍳 🧈 🥞 🧇 🥩 🍗 🍖 🌭 🍔 🍟 🍕 🫓 🥪 🥙 🧆 🌮 🌯 🥗 🥘 🍝 🍜 🍲 🍛 🍣 🍱 🥟 🍙 🍚 🍘 🥠 🍢
                🍧 🍨 🍦 🥧 🧁 🍰 🎂 🍮 🍭 🍬 🍫 🍿 🍩 🍪 🌰 🥜 🍯 🥛 🍼 ☕ 🍵 🧃 🥤 🧋 🍷 🥂 🧊 🥄 🍴 🍽️ 🥣 🥡 🧂
                """,
            ),
        ),
        Category(
            "טבע", "🌻",
            list(
                """
                🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐸 🐵 🐔 🐧 🐦 🐤 🦆 🦅 🦉 🐴 🦄 🐝 🐛 🦋 🐌 🐞 🐢 🐙 🐠 🐟 🐬 🐳
                🌵 🌲 🌳 🌴 🌱 🌿 ☘️ 🍀 🍁 🍂 🍃 🌷 🌹 🌺 🌸 🌼 🌻 🌞 🌝 🌛 🌈 ☀️ ⛅ ☁️ 🌧️ ⛈️ ❄️ ☃️ 💧 🌊
                """,
            ),
        ),
        Category(
            "חפצים", "🛒",
            list(
                """
                🛒 🧺 🛍️ 🎁 🧾 💰 💳 🏷️ 📦 🧹 🧼 🧽 🧴 🪣 🧻 🔑 🔒 💡 🕯️ 📱 💻 📷 📚 📖 ✏️ 📝 📌 📎 ✂️ ⏰ ⌛ 📅
                🏠 🏡 🏪 🏫 🚗 🚕 🚌 🚲 🛵 ✈️ 🚀 ⚽ 🏀 🎾 🏓 🎯 🎮 🎲 🧩 🎨 🎵 🎶 🎧 🎹 🎸 🥁
                """,
            ),
        ),
    )
}
