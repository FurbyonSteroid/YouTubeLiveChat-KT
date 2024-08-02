package com.github.kusaanko.youtubelivechat

class Emoji {

    var emojiId: String? = null
    var shortcuts: List<String>? = null
    var searchTerms: List<String>? = null
    var iconURL: String? = null
    var isCustomEmoji = false

    override fun toString(): String {
        return "Emoji(emojiId=$emojiId, shortcuts=$shortcuts, searchTerms=$searchTerms, iconURL=$iconURL, isCustomEmoji=$isCustomEmoji)"
    }
}