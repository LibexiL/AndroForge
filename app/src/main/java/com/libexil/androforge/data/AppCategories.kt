package com.libexil.androforge.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build

/** Kinds of apps, for folder suggestions. */
enum class AppCategory(val label: String, val keywords: List<String>) {
    GAMES(
        "Games",
        listOf("game", "games", "roblox", "minecraft", "mojang", "supercell", "epicgames", "fortnite", "pubg", "callofduty",
            "activision", "com.ea.", "gameloft", "com.king.", "candy", "clash", "brawl", "pokemon", "nintendo", "steam", "xbox",
            "playstation", "chess", "puzzle", "solitaire", "sudoku", "mihoyo", "hoyoverse", "genshin", "riot", "garena",
            "unity3d", "miniclip", "zynga", "rovio", "ubisoft", "square_enix", "squareenix", "bandai", "netmarble", "nexon",
            "terraria", "stardew", "trove"),
    ),
    SHOPPING(
        "Shopping",
        listOf("amazon.mshop", "shopping", "shop", "ebay", "walmart", "target", "etsy", "aliexpress", "temu", "shein", "wish",
            "bestbuy", "costco", "ikea", "wayfair", "nike", "adidas", "zara", "com.hm.", "mercari", "poshmark", "depop", "offerup",
            "wayfair", "homedepot", "lowes", "macys", "kohls", "sephora", "ulta", "deals", "coupon", "groupon", "stockx"),
    ),
    SOCIAL(
        "Social",
        listOf("facebook.katana", "instagram", "snapchat", "tiktok", "musically", "twitter", "reddit", "discord",
            "pinterest", "tumblr", "threads", "bereal", "linkedin", "mastodon", "bluesky", "social", "com.vkontakte", "weibo", "lemon8"),
    ),
    CHAT(
        "Chat",
        listOf("whatsapp", "telegram", "messenger", "orca", "signal", "messaging", "messages", "sms", "mms", "viber", "line.",
            "wechat", "kik", "skype", "groupme", "imessage", "beeper", "dialer", "contacts", "phone"),
    ),
    MUSIC(
        "Music & audio",
        listOf("spotify", "music", "soundcloud", "pandora", "deezer", "tidal", "audible", "podcast", "shazam", "bandcamp",
            "iheart", "tunein", "audio", "radio", "amazon.mp3", "ytmusic", "poweramp", "musicolet"),
    ),
    VIDEO(
        "Video",
        listOf("youtube", "netflix", "hulu", "disney", "twitch", "primevideo", "amazon.avod", "hbo", "max.", "plex", "jellyfin",
            "crunchyroll", "paramount", "peacock", "tubi", "pluto", "vimeo", "kick.", "video", "vlc", "mxtech", "tv."),
    ),
    PHOTOS(
        "Photos",
        listOf("camera", "gallery", "photos", "photo", "lightroom", "snapseed", "picsart", "vsco", "canva", "capcut", "inshot",
            "remini", "facetune", "picture", "editor"),
    ),
    FINANCE(
        "Money",
        listOf("bank", "banking", "pay", "venmo", "cashapp", "squareup.cash", "paypal", "chase", "wellsfargo", "capitalone",
            "citi", "amex", "americanexpress", "discover", "wallet", "crypto", "coinbase", "robinhood", "fidelity", "schwab", "vanguard", "zelle",
            "mint", "credit", "klarna", "afterpay", "affirm", "binance", "kraken", "finance", "money", "budget"),
    ),
    TRAVEL(
        "Travel & maps",
        listOf("maps", "waze", "uber", "lyft", "airbnb", "booking", "expedia", "tripadvisor", "hopper", "kayak", "transit",
            "citymapper", "delta", "united", "american", "southwest", "airline", "hotel", "travel", "gps", "navigation", "flight"),
    ),
    FOOD(
        "Food",
        listOf("doordash", "ubereats", "grubhub", "postmates", "instacart", "mcdonald", "starbucks", "dunkin", "chipotle",
            "tacobell", "wendys", "burgerking", "dominos", "pizza", "subway", "chickfila", "food", "eats", "recipe", "yelp",
            "opentable", "hellofresh", "kroger", "safeway"),
    ),
    WORK(
        "Productivity",
        listOf("docs", "sheets", "slides", "drive", "office", "word", "excel", "powerpoint", "onenote", "notion", "evernote",
            "calendar", "gmail", "outlook", "mail", "email", "keep", "notes", "todo", "tasks", "slack", "teams", "zoom", "meet",
            "trello", "asana", "dropbox", "onedrive", "scanner", "pdf", "adobe.reader", "obsidian", "todoist", "authenticator"),
    ),
    HEALTH(
        "Health & fitness",
        listOf("fitness", "googlefit", "apps.fitness", "health", "strava", "myfitnesspal", "workout", "gym", "nike.plusgps", "peloton", "fitbit",
            "garmin", "sleep", "calm", "headspace", "meditation", "running", "yoga", "steps"),
    ),
    NEWS(
        "News & reading",
        listOf("news", "nytimes", "cnn", "bbc", "reuters", "flipboard", "feedly", "medium", "kindle", "books", "reader",
            "wattpad", "webtoon", "comics", "manga", "magazine", "weather"),
    ),
    TOOLS(
        "Tools",
        listOf("settings", "calculator", "clock", "files", "filemanager", "myfiles", "flashlight", "compass", "recorder",
            "voicerecorder", "remote", "smartthings", "wearable", "watch", "galaxy", "launcher", "keyboard", "vpn", "cleaner",
            "battery", "wifi", "bluetooth", "qr", "translate", "chrome", "browser", "firefox", "brave", "opera", "duckduckgo",
            "samsung.android.app", "termux", "authenticator", "password", "1password", "bitwarden", "lastpass"),
    );

    companion object {
        private val cache = HashMap<String, AppCategory?>()

        /** Best guess for an app: the category its developer declared, then name/package keywords. */
        @Synchronized
        fun of(context: Context, app: AppInfo): AppCategory? = cache.getOrPut(app.key.pkg) { detect(context, app) }

        private fun detect(context: Context, app: AppInfo): AppCategory? {
            val info: ApplicationInfo? = try {
                context.packageManager.getApplicationInfo(app.key.pkg, 0)
            } catch (e: Exception) {
                null
            }
            if (info != null) {
                @Suppress("DEPRECATION")
                if (info.flags and ApplicationInfo.FLAG_IS_GAME != 0) return GAMES
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    when (info.category) {
                        ApplicationInfo.CATEGORY_GAME -> return GAMES
                        ApplicationInfo.CATEGORY_AUDIO -> return MUSIC
                        ApplicationInfo.CATEGORY_VIDEO -> return VIDEO
                        ApplicationInfo.CATEGORY_IMAGE -> return PHOTOS
                        ApplicationInfo.CATEGORY_SOCIAL -> return SOCIAL
                        ApplicationInfo.CATEGORY_NEWS -> return NEWS
                        ApplicationInfo.CATEGORY_MAPS -> return TRAVEL
                        ApplicationInfo.CATEGORY_PRODUCTIVITY -> return WORK
                    }
                }
            }
            val hay = (app.key.pkg + " " + app.label).lowercase()
            // Most specific categories first, so "Samsung Music" is Music and not Tools.
            val order = listOf(GAMES, SHOPPING, FOOD, FINANCE, MUSIC, VIDEO, SOCIAL, CHAT, TRAVEL, HEALTH, PHOTOS, WORK, NEWS, TOOLS)
            return order.firstOrNull { c -> c.keywords.any { hay.contains(it) } }
        }

        /** A folder name typed by the user that clearly means a category ("games", "shop", "social"...). */
        fun fromName(name: String): AppCategory? {
            val n = name.trim().lowercase()
            if (n.length < 3) return null
            return entries.firstOrNull { c ->
                c.label.lowercase().startsWith(n) || c.label.lowercase().split(" ").any { it.startsWith(n) } ||
                    c.name.lowercase().startsWith(n) || (n.length >= 4 && c.keywords.any { it == n })
            } ?: when {
                n.startsWith("shop") || n.startsWith("buy") -> SHOPPING
                n.startsWith("game") || n.startsWith("play") -> GAMES
                n.startsWith("pic") || n.startsWith("cam") -> PHOTOS
                n.startsWith("bank") || n.startsWith("pay") -> FINANCE
                n.startsWith("msg") || n.startsWith("text") || n.startsWith("messag") -> CHAT
                n.startsWith("stream") || n.startsWith("watch") || n.startsWith("tv") -> VIDEO
                n.startsWith("util") || n.startsWith("sys") -> TOOLS
                else -> null
            }
        }
    }
}
