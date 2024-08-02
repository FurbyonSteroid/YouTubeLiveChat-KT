package com.github.kusaanko.youtubelivechat

import com.github.kusaanko.youtubelivechat.Util.generateClientMessageId
import com.github.kusaanko.youtubelivechat.Util.getJSONList
import com.github.kusaanko.youtubelivechat.Util.getJSONMap
import com.github.kusaanko.youtubelivechat.Util.getJSONValueBoolean
import com.github.kusaanko.youtubelivechat.Util.getJSONValueInt
import com.github.kusaanko.youtubelivechat.Util.getJSONValueLong
import com.github.kusaanko.youtubelivechat.Util.getJSONValueString
import com.github.kusaanko.youtubelivechat.Util.getPageContent
import com.github.kusaanko.youtubelivechat.Util.getPageContentWithJson
import com.github.kusaanko.youtubelivechat.Util.searchJsonElementByKey
import com.github.kusaanko.youtubelivechat.Util.sendHttpRequestWithJson
import com.github.kusaanko.youtubelivechat.Util.toJSON
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.io.IOException
import java.lang.IllegalArgumentException
import java.math.BigInteger
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

class YouTubeLiveChat(id: String, private val isTopChatOnly: Boolean, type: IdType) {
    /**
     * Get video id
     *
     * @return Video id
     */
    var videoId: String? = null
        private set

    /**
     * Get channel id of this live.
     *
     * @return Channel id
     */
    var channelId: String? = null
        private set
    private var continuation: String? = null

    /**
     * Check this live replay is replay.
     *
     * @return If this live is replay, returns true.
     */
    var isReplay: Boolean = false
        private set
    private var visitorData: String? = ""

    /**
     * Get pinned message
     *
     * @return ChatItem
     */
    var bannerItem: ChatItem? = null
        private set
    private val chatItems = ArrayList<ChatItem>()
    private val chatItemTickerPaidMessages = ArrayList<ChatItem>()
    private val chatItemDeletes = ArrayList<ChatItemDelete>()
    private var locale: Locale
    private var clientVersion: String? = null
    private var isInitDataAvailable = false
    private var apiKey: String? = null
    private var datasyncId: String? = null
    private var commentCounter = 0
    private var clientMessageId: String
    private var params: String? = null
    private var cookie: MutableMap<String, String>? = null

    private var sha1: MessageDigest? = null
    private val gson: Gson

    /**
     * Initialize YouTubeLiveChat
     *
     * @param id            Id used in YouTube
     * @param isTopChatOnly Is this top chat only mode
     * @param type          The type of id (VIDEO or CHANNEL)
     * @throws IOException              Http request error
     * @throws IllegalArgumentException Video id is incorrect
     */
    init {
        this.locale = Locale.US
        this.clientMessageId = generateClientMessageId()
        this.gson = Gson()
        try {
            this.getInitialData(id, type)
        } catch (exception: IOException) {
            throw IOException(exception.localizedMessage)
        }
        requireNotNull(this.continuation) { "Invalid " + type.toString().lowercase(Locale.getDefault()) + " id:" + id }
    }

    /**
     * Initialize YouTubeLiveChat using video id
     *
     * @param videoId       Video id used in YouTube
     * @param isTopChatOnly Is this top chat only mode
     * @throws IOException              Http request error
     * @throws IllegalArgumentException Video id is incorrect
     */
    constructor(videoId: String, isTopChatOnly: Boolean) : this(videoId, isTopChatOnly, IdType.VIDEO)

    /**
     * Initialize YouTubeLiveChat using video id.
     * This works with top chat only mode.
     *
     * @param videoId Video id used in YouTube
     * @throws IOException              Http request error
     * @throws IllegalArgumentException Video id is incorrect
     */
    constructor(videoId: String) : this(videoId, true, IdType.VIDEO)

    /**
     * Reset this. If you have an error, try to call this.
     * You don't need to call setLocale() again.
     *
     * @throws IOException Http request error
     */
    @Throws(IOException::class)
    fun reset() {
        this.visitorData = ""
        chatItems.clear()
        chatItemTickerPaidMessages.clear()
        chatItemDeletes.clear()
        this.commentCounter = 0
        this.clientMessageId = generateClientMessageId()
        try {
            this.getInitialData(this.videoId, IdType.VIDEO)
        } catch (exception: IOException) {
            throw IOException(exception.localizedMessage)
        }
    }

    /**
     * Update chat data with offset
     *
     * @param offsetInMs Offset in milli seconds
     * @throws IOException Http request error
     */
    /**
     * Update chat data
     *
     * @throws IOException Http request error
     */
    @JvmOverloads
    @Throws(IOException::class)
    fun update(offsetInMs: Long = 0) {
        if (this.isInitDataAvailable) {
            this.isInitDataAvailable = false
            return
        }
        chatItems.clear()
        chatItemTickerPaidMessages.clear()
        chatItemDeletes.clear()
        try {
            //Get live actions
            if (this.continuation == null) {
                throw IOException("continuation is null! Please call reset().")
            }
            val pageContent = getPageContentWithJson(
                (if (this.isReplay) liveChatReplayApi else liveChatApi) + this.apiKey,
                this.getPayload(offsetInMs),
                header
            )
            val json: Map<String, Any> = toJSON(pageContent!!)
            if (this.visitorData == null || visitorData!!.isEmpty()) {
                this.visitorData = getJSONValueString(getJSONMap(json, "responseContext"), "visitorData")
            }
            //Get clientVersion
            val serviceTrackingParams = getJSONList(json, "serviceTrackingParams", "responseContext")
            if (serviceTrackingParams != null) {
                for (ser in serviceTrackingParams) {
                    val service = ser as Map<String, Any>
                    val serviceName = getJSONValueString(service, "service")
                    if (serviceName != null && serviceName == "CSI") {
                        val params = getJSONList(service, "params")
                        if (params != null) {
                            for (par in params) {
                                val param = par as Map<String, Any>
                                val key = getJSONValueString(param, "key")
                                if (key != null && key == "cver") {
                                    this.clientVersion = getJSONValueString(param, "value")
                                }
                            }
                        }
                    }
                }
            }
            //Parse actions and update continuation
            val liveChatContinuation = getJSONMap(json, "continuationContents", "liveChatContinuation")
            if (this.isReplay) {
                if (liveChatContinuation.isNotEmpty()) {
                    val actions = getJSONList(liveChatContinuation, "actions")
                    if (actions != null) {
                        this.parseActions(actions)
                    }
                }
                val continuations = getJSONList(liveChatContinuation, "continuations")
                //Update continuation
                if (continuations != null) {
                    for (co in continuations) {
                        val continuation = co as Map<String, Any>
                        val value = getJSONValueString(
                            getJSONMap(continuation, "liveChatReplayContinuationData"),
                            "continuation"
                        )
                        if (value != null) {
                            this.continuation = value
                        }
                    }
                }
            } else {
                if (liveChatContinuation.isNotEmpty()) {
                    val actions = getJSONList(liveChatContinuation, "actions")
                    if (actions != null) {
                        this.parseActions(actions)
                    }
                    val continuations = getJSONList(liveChatContinuation, "continuations")
                    if (continuations != null) {
                        for (co in continuations) {
                            val continuation = co as Map<String, Any>
                            this.continuation = getJSONValueString(
                                getJSONMap(continuation, "invalidationContinuationData"),
                                "continuation"
                            )
                            if (this.continuation == null) {
                                this.continuation = getJSONValueString(
                                    getJSONMap(continuation, "timedContinuationData"),
                                    "continuation"
                                )
                            }
                            if (this.continuation == null) {
                                this.continuation = getJSONValueString(
                                    getJSONMap(continuation, "reloadContinuationData"),
                                    "continuation"
                                )
                            }
                        }
                    }
                }
            }
        } catch (exception: IOException) {
            throw IOException("Can't get youtube live chat!", exception)
        }
    }

    /**
     * Send a message to this live chat
     * You need to set user data using setUserData() before calling this method
     *
     * @param message Chat message to send
     * @throws IOException           Http request error
     * @throws IllegalStateException The IDs are not set error
     */
    @Throws(IOException::class, IllegalStateException::class)
    fun sendMessage(message: String) {
        check(!this.isReplay) { "This live is replay! You can send a message if this live isn't replay." }
        check(!this.isIDsMissing) { "You need to set user data using setUserData()" }

        try {
            if (this.datasyncId == null) {
                throw IOException("datasyncId is null! Please call reset() or set user data.")
            }
            checkNotNull(this.params) { "params is null! You may not set appropriate Cookie. Please call reset()." }
            sendHttpRequestWithJson(
                liveChatSendMessageApi + this.apiKey,
                this.getPayloadToSendMessage(message),
                header
            )
        } catch (exception: IOException) {
            throw IOException("Couldn't send a message!", exception)
        }
    }

    @Throws(IOException::class, IllegalStateException::class)
    fun deleteMessage(chatItem: ChatItem) {
        check(!this.isReplay) { "This live is replay! You can delete a chat if this live isn't replay." }

        try {
            if (this.datasyncId == null) {
                throw IOException("datasyncId is null! Please call reset() or set user data.")
            }
            if (chatItem.chatDeleteParams == null) {
                check(!this.isIDsMissing) { "You need to set user data using setUserData()" }
                getContextMenu(chatItem)
                checkNotNull(chatItem.chatDeleteParams) { "chatDeleteParams is null! Check if you have permission or use setUserData() first." }
            }
            sendHttpRequestWithJson(
                liveChatModerateApi + this.apiKey,
                this.getPayloadClient(chatItem.chatDeleteParams),
                header
            )
        } catch (exception: IOException) {
            throw IOException("Couldn't delete chat!", exception)
        }
    }

    @Throws(IOException::class, IllegalStateException::class)
    fun banAuthorTemporarily(chatItem: ChatItem) {
        check(!this.isReplay) { "This live is replay! You can ban a user if this live isn't replay." }

        try {
            if (this.datasyncId == null) {
                throw IOException("datasyncId is null! Please call reset() or set user data.")
            }
            if (chatItem.timeBanParams == null) {
                check(!this.isIDsMissing) { "You need to set user data using setUserData()" }
                getContextMenu(chatItem)
                checkNotNull(chatItem.timeBanParams) { "timeBanParams is null! Check if you have permission or use setUserData() first." }
            }
            sendHttpRequestWithJson(
                liveChatModerateApi + this.apiKey,
                this.getPayloadClient(chatItem.timeBanParams),
                header
            )
        } catch (exception: IOException) {
            throw IOException("Couldn't ban user!", exception)
        }
    }

    @Throws(IOException::class, IllegalStateException::class)
    fun banUserPermanently(chatItem: ChatItem) {
        check(!this.isReplay) { "This live is replay! You can ban a user if this live isn't replay." }
        check(!this.isIDsMissing) { "You need to set user data using setUserData()" }

        try {
            if (this.datasyncId == null) {
                throw IOException("datasyncId is null! Please call reset() or set user data.")
            }
            if (chatItem.userBanParams == null) {
                getContextMenu(chatItem)
                checkNotNull(chatItem.userBanParams) { "userBanParams is null! Check if you have permission or use setUserData() first." }
            }
            sendHttpRequestWithJson(
                liveChatModerateApi + this.apiKey,
                this.getPayloadClient(chatItem.userBanParams),
                header
            )
        } catch (exception: IOException) {
            throw IOException("Couldn't ban user!", exception)
        }
    }

    @Throws(IOException::class, IllegalStateException::class)
    fun unbanUser(chatItem: ChatItem) {
        check(!this.isReplay) { "This live is replay! You can ban a user if this live isn't replay." }
        check(!this.isIDsMissing) { "You need to set user data using setUserData()" }

        try {
            if (this.datasyncId == null) {
                throw IOException("datasyncId is null! Please call reset() or set user data.")
            }
            if (chatItem.userUnbanParams == null) {
                getContextMenu(chatItem)
                checkNotNull(chatItem.userUnbanParams) { "userUnbanParams is null! Check if you have permission or use setUserData() first." }
            }
            sendHttpRequestWithJson(
                liveChatModerateApi + this.apiKey,
                this.getPayloadClient(chatItem.userUnbanParams),
                header
            )
        } catch (exception: IOException) {
            throw IOException("Couldn't unban user!", exception)
        }
    }

    @Throws(IOException::class, IllegalStateException::class)
    fun pinMessage(chatItem: ChatItem) {
        check(!this.isReplay) { "This live is replay! You can pin a chat if this live isn't replay." }

        try {
            if (this.datasyncId == null) {
                throw IOException("datasyncId is null! Please call reset() or set user data.")
            }
            if (chatItem.pinToTopParams == null) {
                check(!this.isIDsMissing) { "You need to set user data using setUserData()" }
                getContextMenu(chatItem)
                checkNotNull(chatItem.pinToTopParams) { "pinToTopParams is null! Check if you have permission or use setUserData() first." }
            }
            sendHttpRequestWithJson(
                liveChatActionApi + this.apiKey,
                this.getPayloadClient(chatItem.pinToTopParams),
                header
            )
        } catch (exception: IOException) {
            throw IOException("Couldn't pin chat!", exception)
        }
    }

    /**
     * Language used to get chat
     * Default locale is Locale.US(en_US)
     * setLocale(Locale.US);
     *
     * @param locale Language (country code and language code are required)
     */
    fun setLocale(locale: Locale) {
        require(!(locale.country == null || locale.country.isEmpty())) { "Locale must be set country!" }
        require(!(locale.language == null || locale.language.isEmpty())) { "Locale must be set language!" }
        this.locale = locale
    }

    /**
     * Set user data.
     * Cookies can be found in Chrome Devtools(F12) 'Network' tab, 'get_live_chat' request.
     * You need all cookies.
     *
     * @param cookie Cookie
     *
     * @throws IOException Http request error
     */
    @Throws(IOException::class)
    fun setUserData(cookie: MutableMap<String, String>?) {
        this.cookie = cookie
        this.reset()
    }

    /**
     * Set user data.
     * Cookies can be found in Chrome Devtools(F12) 'Network' tab, 'get_live_chat' request.
     * You need all cookies.
     *
     * @param cookie Cookie
     *
     * @throws IOException Http request error
     */
    @Throws(IOException::class)
    fun setUserData(cookie: String) {
        val cookies = cookie.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        this.cookie = HashMap()
        for (c in cookies) {
            (this.cookie as HashMap<String, String>)[c.substring(0, c.indexOf("=")).trim { it <= ' ' }] =
                c.substring(c.indexOf("=") + 1).trim { it <= ' ' }
        }
        this.reset()
    }

    private fun parseActions(json: List<Any>) {
        for (i in json) {
            val actions = i as Map<String, Any>
            val addChatItemAction: Map<String, Any> = getJSONMap(actions, "addChatItemAction")
            //For replay
            if (addChatItemAction == null) {
                val replayChatItemAction: Map<String, Any> = getJSONMap(actions, "replayChatItemAction")
                if (replayChatItemAction != null) {
                    val acts = getJSONList(replayChatItemAction, "actions")
                    if (acts != null) {
                        parseActions(acts)
                    }
                }
            }
            if (addChatItemAction != null) {
                var chatItem: ChatItem? = null
                val item: Map<String, Any> = getJSONMap(addChatItemAction, "item")
                if (item != null) {
                    chatItem = ChatItem(this)
                    this.parseChatItem(chatItem, item)
                }
                if (chatItem?.id != null) {
                    chatItems.add(chatItem)
                }
            }
            //Pinned message
            val contents: Map<String, Any> = getJSONMap(
                actions,
                "addBannerToLiveChatCommand",
                "bannerRenderer",
                "liveChatBannerRenderer",
                "contents"
            )
            if (contents != null) {
                val chatItem = ChatItem(this)
                this.parseChatItem(chatItem, contents)
                this.bannerItem = chatItem
            }
            val markChatItemAsDeletedAction: Map<String, Any> = getJSONMap(actions, "markChatItemAsDeletedAction")
            if (markChatItemAsDeletedAction != null) {
                val chatItemDelete = ChatItemDelete()
                chatItemDelete.message =
                    this.parseMessage(getJSONMap(markChatItemAsDeletedAction, "deletedStateMessage"), ArrayList())
                chatItemDelete.targetId = getJSONValueString(markChatItemAsDeletedAction, "targetItemId")
                chatItemDeletes.add(chatItemDelete)
            }
        }
    }

    private fun parseChatItem(chatItem: ChatItem, action: Map<String, Any>) {
        var liveChatTextMessageRenderer: Map<String, Any>? = getJSONMap(action, "liveChatTextMessageRenderer")
        val liveChatPaidMessageRenderer: Map<String, Any> = getJSONMap(action, "liveChatPaidMessageRenderer")
        val liveChatPaidStickerRenderer: Map<String, Any> = getJSONMap(action, "liveChatPaidStickerRenderer")
        val liveChatMembershipItemRenderer: Map<String, Any> = getJSONMap(action, "liveChatMembershipItemRenderer")
        if (liveChatTextMessageRenderer == null && liveChatPaidMessageRenderer != null) {
            liveChatTextMessageRenderer = liveChatPaidMessageRenderer
        }
        if (liveChatTextMessageRenderer == null && liveChatPaidStickerRenderer != null) {
            liveChatTextMessageRenderer = liveChatPaidStickerRenderer
        }
        if (liveChatTextMessageRenderer == null && liveChatMembershipItemRenderer != null) {
            liveChatTextMessageRenderer = liveChatMembershipItemRenderer
        }
        if (liveChatTextMessageRenderer != null) {
            chatItem.authorName =
                getJSONValueString(getJSONMap(liveChatTextMessageRenderer, "authorName"), "simpleText")
            chatItem.id = getJSONValueString(liveChatTextMessageRenderer, "id")
            chatItem.authorChannelID = getJSONValueString(liveChatTextMessageRenderer, "authorExternalChannelId")
            val message: Map<String, Any> = getJSONMap(liveChatTextMessageRenderer, "message")
            chatItem.messageExtended = ArrayList()
            chatItem.message = parseMessage(message, chatItem.messageExtended as ArrayList<Any>)
            val authorPhotoThumbnails = getJSONList(liveChatTextMessageRenderer, "thumbnails", "authorPhoto")
            if (authorPhotoThumbnails != null) {
                chatItem.authorIconURL = this.getJSONThumbnailURL(authorPhotoThumbnails)
            }
            val timestampStr = getJSONValueString(liveChatTextMessageRenderer, "timestampUsec")
            if (timestampStr != null) {
                chatItem.timestamp = timestampStr.toLong()
            }
            val authorBadges = getJSONList(liveChatTextMessageRenderer, "authorBadges")
            if (authorBadges != null) {
                for (au in authorBadges) {
                    val authorBadge = au as Map<String, Any>
                    val liveChatAuthorBadgeRenderer: Map<String, Any> =
                        getJSONMap(authorBadge, "liveChatAuthorBadgeRenderer")
                    if (liveChatAuthorBadgeRenderer != null) {
                        val type = getJSONValueString(getJSONMap(liveChatAuthorBadgeRenderer, "icon"), "iconType")
                        if (type != null) {
                            when (type) {
                                "VERIFIED" -> chatItem.authorType.add(AuthorType.VERIFIED)
                                "OWNER" -> chatItem.authorType.add(AuthorType.OWNER)
                                "MODERATOR" -> chatItem.authorType.add(AuthorType.MODERATOR)
                            }
                        }
                        val customThumbnail: Map<String, Any> =
                            getJSONMap(liveChatAuthorBadgeRenderer, "customThumbnail")
                        if (customThumbnail != null) {
                            chatItem.authorType.add(AuthorType.MEMBER)
                            val thumbnails = customThumbnail["thumbnails"] as List<Any>?
                            chatItem.memberBadgeIconURL = this.getJSONThumbnailURL(thumbnails!!)
                        }
                    }
                }
            }
            // Context Menu Params
            val contextMenuParams = getJSONValueString(
                getJSONMap(
                    liveChatTextMessageRenderer,
                    "contextMenuEndpoint",
                    "liveChatItemContextMenuEndpoint"
                ), "params"
            )
            if (contextMenuParams != null) {
                chatItem.contextMenuParams = contextMenuParams
            }
        }
        if (action.containsKey("liveChatViewerEngagementMessageRenderer")) {
            val liveChatViewerEngagementMessageRenderer =
                action["liveChatViewerEngagementMessageRenderer"] as Map<String, Any>
            chatItem.authorName = "YouTube"
            chatItem.authorChannelID = "user/YouTube"
            chatItem.authorType.add(AuthorType.YOUTUBE)
            chatItem.id = getJSONValueString(liveChatViewerEngagementMessageRenderer, "id")
            chatItem.messageExtended = ArrayList()
            chatItem.message = this.parseMessage(
                getJSONMap(liveChatViewerEngagementMessageRenderer, "message"),
                chatItem.messageExtended as ArrayList<Any>
            )
            val timestampStr = getJSONValueString(liveChatViewerEngagementMessageRenderer, "timestampUsec")
            if (timestampStr != null) {
                chatItem.timestamp = timestampStr.toLong()
            }
        }
        if (liveChatPaidMessageRenderer != null) {
            chatItem.bodyBackgroundColor = getJSONValueInt(liveChatPaidMessageRenderer, "bodyBackgroundColor")
            chatItem.bodyTextColor = getJSONValueInt(liveChatPaidMessageRenderer, "bodyBackgroundColor")
            chatItem.headerBackgroundColor = getJSONValueInt(liveChatPaidMessageRenderer, "bodyBackgroundColor")
            chatItem.headerTextColor = getJSONValueInt(liveChatPaidMessageRenderer, "bodyBackgroundColor")
            chatItem.authorNameTextColor = getJSONValueInt(liveChatPaidMessageRenderer, "authorNameTextColor")
            chatItem.purchaseAmount =
                getJSONValueString(getJSONMap(liveChatPaidMessageRenderer, "purchaseAmountText"), "simpleText")
            chatItem.type = ChatItemType.PAID_MESSAGE
        }
        if (liveChatPaidStickerRenderer != null) {
            chatItem.backgroundColor = getJSONValueInt(liveChatPaidStickerRenderer, "backgroundColor")
            chatItem.purchaseAmount =
                getJSONValueString(getJSONMap(liveChatPaidStickerRenderer, "purchaseAmountText"), "simpleText")
            val thumbnails = getJSONList(liveChatPaidStickerRenderer, "thumbnails", "sticker")
            if (thumbnails != null) {
                chatItem.stickerIconURL = this.getJSONThumbnailURL(thumbnails)
            }
            chatItem.type = ChatItemType.PAID_STICKER
        }
        val liveChatTickerPaidMessageItemRenderer: Map<String, Any> =
            getJSONMap(action, "liveChatTickerPaidMessageItemRenderer")
        if (liveChatTickerPaidMessageItemRenderer != null) {
            val renderer: Map<String, Any> =
                getJSONMap(liveChatPaidMessageRenderer, "showItemEndpoint", "showLiveChatItemEndpoint", "renderer")
            this.parseChatItem(chatItem, renderer)
            chatItem.endBackgroundColor = getJSONValueInt(liveChatPaidMessageRenderer, "endBackgroundColor")
            chatItem.durationSec = getJSONValueInt(liveChatPaidMessageRenderer, "durationSec")
            chatItem.fullDurationSec = getJSONValueInt(liveChatPaidMessageRenderer, "fullDurationSec")
            chatItem.type = ChatItemType.TICKER_PAID_MESSAGE
        }
        if (liveChatMembershipItemRenderer != null) {
            chatItem.messageExtended = ArrayList()
            chatItem.message =
                this.parseMessage(
                    getJSONMap(liveChatMembershipItemRenderer, "headerSubtext"),
                    chatItem.messageExtended as ArrayList<Any>
                )
            chatItem.type = ChatItemType.NEW_MEMBER_MESSAGE
        }
    }

    private fun getJSONThumbnailURL(thumbnails: List<Any>): String? {
        var size: Long = 0
        var url: String? = null
        for (tObj in thumbnails) {
            val thumbnail = tObj as Map<String, Any>
            val width = getJSONValueLong(thumbnail, "width")
            val u = getJSONValueString(thumbnail, "url")
            if (u != null) {
                if (size <= width) {
                    size = width
                    url = u
                }
            }
        }
        return url
    }

    private fun parseMessage(message: Map<String, Any>, messageExtended: MutableList<Any>): String? {
        var text = StringBuilder()
        val runs = getJSONList(message, "runs")
        if (runs != null) {
            text = StringBuilder()
            for (runObj in runs) {
                val run = runObj as Map<String, Any>
                if (run.containsKey("text")) {
                    text.append(run["text"].toString())
                    messageExtended.add(Text(run["text"].toString()))
                }
                val emojiMap: Map<String, Any> = getJSONMap(run, "emoji")
                if (emojiMap != null) {
                    val emoji = Emoji()
                    emoji.emojiId = getJSONValueString(emojiMap, "emojiId")
                    val shortcutsList = getJSONList(emojiMap, "shortcuts")
                    val shortcuts = ArrayList<String>()
                    if (shortcutsList != null) {
                        for (s in shortcutsList) {
                            shortcuts.add(s.toString())
                        }
                    }
                    emoji.shortcuts = shortcuts
                    if (!shortcuts.isEmpty()) text.append(" ").append(shortcuts[0]).append(" ")
                    val searchTermsList = getJSONList(emojiMap, "searchTerms")
                    val searchTerms = ArrayList<String>()
                    if (searchTermsList != null) {
                        for (s in searchTermsList) {
                            searchTerms.add(s.toString())
                        }
                    }
                    emoji.searchTerms = searchTerms
                    val thumbnails = getJSONList(emojiMap, "thumbnails", "image")
                    if (thumbnails != null) {
                        emoji.iconURL = this.getJSONThumbnailURL(thumbnails)
                    }
                    emoji.isCustomEmoji = getJSONValueBoolean(emojiMap, "isCustomEmoji")
                    messageExtended.add(emoji)
                }
            }
        }
        return if (text.length == 0) null else text.toString()
    }

    /**
     * Get list of ChatItem
     *
     * @return List of ChatItem
     */
    fun getChatItems(): ArrayList<ChatItem> {
        return chatItems.clone() as ArrayList<ChatItem>
    }

    /**
     * Get list of ChatItemDelete
     *
     * @return List of ChatItemDelete
     */
    fun getChatItemDeletes(): ArrayList<ChatItemDelete> {
        return chatItemDeletes.clone() as ArrayList<ChatItemDelete>
    }

    val chatTickerPaidMessages: ArrayList<ChatItem>
        /**
         * Get list of ChatItem(type=TICKER_PAID_MESSAGE)
         *
         * @return List of ChatItem
         */
        get() = chatItemTickerPaidMessages.clone() as ArrayList<ChatItem>

    @Throws(IOException::class)
    private fun getInitialData(id: String?, type: IdType) {
        this.isInitDataAvailable = true
        run {
            var html: String? = ""
            if (type == IdType.VIDEO) {
                this.videoId = id
                html = getPageContent(
                    "https://www.youtube.com/watch?v=$id",
                    header
                )
                val channelIdMatcher = Pattern.compile("\"channelId\":\"([^\"]*)\",\"isOwnerViewing\"")
                    .matcher(Objects.requireNonNull(html))
                if (channelIdMatcher.find()) {
                    this.channelId = channelIdMatcher.group(1)
                }
            } else if (type == IdType.CHANNEL) {
                this.channelId = id
                html = getPageContent(
                    "https://www.youtube.com/channel/$id/live",
                    header
                )
                val videoIdMatcher = Pattern.compile("\"updatedMetadataEndpoint\":\\{\"videoId\":\"([^\"]*)").matcher(
                    Objects.requireNonNull(html)
                )
                if (videoIdMatcher.find()) {
                    this.videoId = videoIdMatcher.group(1)
                } else {
                    throw IOException("The channel (ID:" + this.channelId + ") has not started live streaming!")
                }
            }
            val isReplayMatcher = Pattern.compile("\"isReplay\":([^,]*)").matcher(html)
            if (isReplayMatcher.find()) {
                this.isReplay = isReplayMatcher.group(1).toBoolean()
            }
            val topOnlyContinuationMatcher =
                Pattern.compile("\"selected\":true,\"continuation\":\\{\"reloadContinuationData\":\\{\"continuation\":\"([^\"]*)")
                    .matcher(html)
            if (topOnlyContinuationMatcher.find()) {
                this.continuation = topOnlyContinuationMatcher.group(1)
            }
            if (!this.isTopChatOnly) {
                val allContinuationMatcher =
                    Pattern.compile("\"selected\":false,\"continuation\":\\{\"reloadContinuationData\":\\{\"continuation\":\"([^\"]*)")
                        .matcher(html)
                if (allContinuationMatcher.find()) {
                    this.continuation = allContinuationMatcher.group(1)
                }
            }
            val innertubeApiKeyMatcher = Pattern.compile("\"innertubeApiKey\":\"([^\"]*)\"").matcher(html)
            if (innertubeApiKeyMatcher.find()) {
                this.apiKey = innertubeApiKeyMatcher.group(1)
            }
            val datasyncIdMatcher = Pattern.compile("\"datasyncId\":\"([^|]*)\\|\\|.*\"").matcher(html)
            if (datasyncIdMatcher.find()) {
                this.datasyncId = datasyncIdMatcher.group(1)
            }
            if (this.isReplay) {
                html = getPageContent(
                    "https://www.youtube.com/live_chat_replay?continuation=" + this.continuation + "",
                    HashMap()
                )
                var initJson = Objects.requireNonNull(html)!!
                    .substring(html!!.indexOf("window[\"ytInitialData\"] = ") + "window[\"ytInitialData\"] = ".length)
                initJson = initJson.substring(0, initJson.indexOf(";</script>"))
                val json: Map<String, Any> = toJSON(initJson)
                val timedContinuationData = getJSONMap(
                    json,
                    "continuationContents",
                    "liveChatContinuation",
                    "continuations",
                    0,
                    "liveChatReplayContinuationData"
                )
                if (timedContinuationData != null) {
                    this.continuation = timedContinuationData["continuation"].toString()
                }
                val actions = getJSONList(json, "actions", "continuationContents", "liveChatContinuation")
                if (actions != null) {
                    this.parseActions(actions)
                }
            } else {
                html = getPageContent(
                    "https://www.youtube.com/live_chat?continuation=" + this.continuation + "",
                    header
                )
                var initJson = Objects.requireNonNull(html)!!
                    .substring(html!!.indexOf("window[\"ytInitialData\"] = ") + "window[\"ytInitialData\"] = ".length)
                initJson = initJson.substring(0, initJson.indexOf(";</script>"))
                val json = toJSON(initJson)
                val sendLiveChatMessageEndpoint: Map<String, Any> = getJSONMap(
                    json,
                    "continuationContents",
                    "liveChatContinuation",
                    "actionPanel",
                    "liveChatMessageInputRenderer",
                    "sendButton",
                    "buttonRenderer",
                    "serviceEndpoint",
                    "sendLiveChatMessageEndpoint"
                )
                if (sendLiveChatMessageEndpoint != null) {
                    this.params = sendLiveChatMessageEndpoint["params"].toString()
                }
                this.isInitDataAvailable = false
            }
        }
    }

    private fun getClientVersion(): String {
        if (this.clientVersion != null) {
            return clientVersion!!
        }
        val format = SimpleDateFormat("yyyyMMdd")
        return "2." + format.format(Date(System.currentTimeMillis() - (24 * 60 * 1000))) + ".06.00"
    }

    private fun getPayload(offsetInMs: Long): String {
        var offsetInMs = offsetInMs
        if (offsetInMs < 0) {
            offsetInMs = 0
        }
        val json: MutableMap<String, Any> = LinkedHashMap()
        val context: MutableMap<String, Any> = LinkedHashMap()
        val client: MutableMap<String, Any> = LinkedHashMap()
        json["context"] = context
        context["client"] = client
        visitorData?.let { client["visitorData"] = it }
        client["userAgent"] = userAgent
        client["clientName"] = "WEB"
        client["clientVersion"] = getClientVersion()
        client["gl"] = locale.country
        client["hl"] = locale.language
        continuation?.let { json["continuation"] = it }
        if (this.isReplay) {
            val state = LinkedHashMap<String, Any>()
            state["playerOffsetMs"] = offsetInMs.toString()
            json["currentPlayerState"] = state
        }

        return toJSON(json)
    }

    private fun getPayloadToSendMessage(message: String): String {
        val json: MutableMap<String, Any> = LinkedHashMap()
        val context: MutableMap<String, Any> = LinkedHashMap()
        val user: MutableMap<String, Any> = LinkedHashMap()
        val richMessage: MutableMap<String, Any> = LinkedHashMap()
        val textSegments: MutableMap<String, Any> = LinkedHashMap()
        val client: MutableMap<String, Any> = LinkedHashMap()
        if (this.commentCounter >= Int.MAX_VALUE - 1) {
            this.commentCounter = 0
        }
        json["clientMessageId"] = clientMessageId + commentCounter++
        json["context"] = context
        context["client"] = client
        client["clientName"] = "WEB"
        client["clientVersion"] = getClientVersion()
        context["user"] = user
        datasyncId?.let {
            user["onBehalfOfUser"] = it
        }
        params?.let {
            json["params"] = it
        }
        json["richMessage"] = richMessage
        richMessage["textSegments"] = textSegments
        textSegments["text"] = message

        return toJSON(json)
    }

    private fun getPayloadClient(params: String?): String {
        val json: MutableMap<String, Any> = LinkedHashMap()
        val context: MutableMap<String, Any> = LinkedHashMap()
        val user: MutableMap<String, Any> = LinkedHashMap()
        val client: MutableMap<String, Any> = LinkedHashMap()
        if (this.commentCounter >= Int.MAX_VALUE - 1) {
            this.commentCounter = 0
        }
        json["context"] = context
        context["client"] = client
        client["clientName"] = "WEB"
        client["clientVersion"] = getClientVersion()
        context["user"] = user
        datasyncId?.let {
            user["onBehalfOfUser"] = it
        }
        params?.let {
            json["params"] = it
        }

        return toJSON(json)
    }

    private val header: MutableMap<String, String>
        get() {
            val header = HashMap<String, String>()
            if (this.isIDsMissing) return header
            val time = (System.currentTimeMillis() / 1000).toString() + ""
            val origin = "https://www.youtube.com"
            // Find SAPISID
            val SAPISID =
                if (this.cookie != null && cookie!!.containsKey("SAPISID")) cookie!!["SAPISID"] else ""
            val hash = "$time $SAPISID $origin"
            val sha1_result = sHA1Engine!!.digest(hash.toByteArray())

            header["Authorization"] =
                "SAPISIDHASH " + time + "_" + String.format("%040x", BigInteger(1, sha1_result))
            header["X-Origin"] = origin
            header["Origin"] = origin
            if (this.cookie != null) {
                val cookie = StringBuilder()
                for ((key, value) in this.cookie!!) {
                    cookie.append(key).append("=").append(value).append(";")
                }
                header["Cookie"] = cookie.toString()
            }
            return header
        }

    fun getContextMenu(chatItem: ChatItem) {
        try {
            val rawJson = getPageContentWithJson(
                liveChatContextMenuApi + apiKey + "&params=" + chatItem.contextMenuParams, getPayloadToSendMessage(""),
                header
            )
            val json: Map<String, Any> = toJSON(Objects.requireNonNull(rawJson) ?: "{}")
            val items = getJSONList(json, "items", "liveChatItemContextMenuSupportedRenderers", "menuRenderer")
            if (items != null) {
                for (obj in items) {
                    val item = obj as Map<String, Any>
                    val menuServiceItemRenderer: Map<String, Any> = getJSONMap(item, "menuServiceItemRenderer")
                    if (menuServiceItemRenderer != null) {
                        val iconType = getJSONValueString(getJSONMap(menuServiceItemRenderer, "icon"), "iconType")
                        if (iconType != null) {
                            when (iconType) {
                                "KEEP" -> chatItem.pinToTopParams = getJSONValueString(
                                    getJSONMap(
                                        menuServiceItemRenderer,
                                        "serviceEndpoint",
                                        "liveChatActionEndpoint"
                                    ), "params"
                                )

                                "DELETE" -> chatItem.chatDeleteParams = getJSONValueString(
                                    getJSONMap(
                                        menuServiceItemRenderer,
                                        "serviceEndpoint",
                                        "moderateLiveChatEndpoint"
                                    ), "params"
                                )

                                "HOURGLASS" -> chatItem.timeBanParams = getJSONValueString(
                                    getJSONMap(
                                        menuServiceItemRenderer,
                                        "serviceEndpoint",
                                        "moderateLiveChatEndpoint"
                                    ), "params"
                                )

                                "REMOVE_CIRCLE" -> chatItem.userBanParams = getJSONValueString(
                                    getJSONMap(
                                        menuServiceItemRenderer,
                                        "serviceEndpoint",
                                        "moderateLiveChatEndpoint"
                                    ), "params"
                                )

                                "ADD_CIRCLE" -> chatItem.userUnbanParams = getJSONValueString(
                                    getJSONMap(
                                        menuServiceItemRenderer,
                                        "serviceEndpoint",
                                        "moderateLiveChatEndpoint"
                                    ), "params"
                                )

                                "FLAG", "ADD_MODERATOR", "REMOVE_MODERATOR" -> {}
                                else -> {}
                            }
                        }
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private val sHA1Engine: MessageDigest?
        get() {
            if (this.sha1 == null) {
                try {
                    this.sha1 = MessageDigest.getInstance("SHA-1")
                } catch (e: NoSuchAlgorithmException) {
                    e.printStackTrace()
                }
            }
            return this.sha1
        }

    private val isIDsMissing: Boolean
        get() = this.cookie == null

    @get:Throws(IOException::class)
    val broadcastInfo: LiveBroadcastDetails
        /**
         * Get broadcast info
         *
         * @return LiveBroadcastDetails obj
         *
         * @throws IOException Couldn't get broadcast info
         */
        get() {
            try {
                val url =
                    liveStreamInfoApi + this.videoId + "&hl=en&pbj=1"
                val header =
                    HashMap<String, String>()
                header["x-youtube-client-name"] = "1"
                header["x-youtube-client-version"] = getClientVersion()
                val response = getPageContent(url, header)
                val jsonElement: JsonElement =
                    JsonParser.parseString(Objects.requireNonNull(response))
                        .asJsonObject
                val liveBroadcastDetails =
                    searchJsonElementByKey("liveBroadcastDetails", jsonElement)
                return gson.fromJson(liveBroadcastDetails, LiveBroadcastDetails::class.java)
            } catch (exception: IOException) {
                throw IOException("Couldn't get broadcast info!", exception)
            } catch (exception: NullPointerException) {
                throw IOException("Couldn't get broadcast info!", exception)
            }
        }

    companion object {
        /**
         * This is user agent used by YouTubeLiveChat.
         * You can edit this.
         */
        var userAgent: String =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/98.0.4758.102 Safari/537.36,gzip(gfe)"

        private const val liveChatApi =
            "https://www.youtube.com/youtubei/v1/live_chat/get_live_chat?key=" // view live chat
        private const val liveChatReplayApi =
            "https://www.youtube.com/youtubei/v1/live_chat/get_live_chat_replay?key=" // view chat replay
        private const val liveChatSendMessageApi =
            "https://www.youtube.com/youtubei/v1/live_chat/send_message?key=" // send chat
        private const val liveChatContextMenuApi =
            "https://www.youtube.com/youtubei/v1/live_chat/get_item_context_menu?key=" // get chat item menu
        private const val liveChatModerateApi =
            "https://studio.youtube.com/youtubei/v1/live_chat/moderate?key=" // moderation (delete, ban, unban)
        private const val liveChatActionApi =
            "https://studio.youtube.com/youtubei/v1/live_chat/live_chat_action?key=" // tools (pin)
        private const val liveStreamInfoApi = "https://www.youtube.com/watch?v=" // stream info

        /**
         * Get video id from url
         *
         * @param url Full url(example https://www.youtube.com/watch?v=Aw5b1sa0w)
         * @return Video id
         * @throws IllegalArgumentException URL format is incorrect
         */
        fun getVideoIdFromURL(url: String): String {
            var id = url
            if (!id.contains("?") && !id.contains(".com/") && !id.contains(".be/") && !id.contains("/") && !id.contains(
                    "&"
                )
            ) {
                return id
            }
            if (id.contains("youtube.com/watch?")) {
                while (id.contains("v=")) {
                    id = id.substring(id.indexOf("v=") - 1)
                    if (id.startsWith("?") || id.startsWith("&")) {
                        id = id.substring(3)
                        if (id.contains("&")) {
                            id = id.substring(0, id.indexOf("&"))
                        }
                        if (!id.contains("?")) {
                            return id
                        }
                    } else {
                        id = id.substring(3)
                    }
                }
            }
            if (id.contains("youtube.com/embed/")) {
                id = id.substring(id.indexOf("embed/") + 6)
                if (id.contains("?")) {
                    id = id.substring(0, id.indexOf("?"))
                }
                return id
            }
            if (id.contains("youtu.be/")) {
                id = id.substring(id.indexOf("youtu.be/") + 9)
                if (id.contains("?")) {
                    id = id.substring(0, id.indexOf("?"))
                }
                return id
            }
            throw IllegalArgumentException(url)
        }

        /**
         * Get channel id from url
         *
         * @param url Full url(example https://www.youtube.com/channel/USWmbkAWEKOG43WAnbw)
         * @return Channel id
         * @throws IllegalArgumentException URL format is incorrect
         */
        fun getChannelIdFromURL(url: String): String {
            var id = url
            if (!id.contains("?") && !id.contains(".com/") && !id.contains(".be/") && !id.contains("/") && !id.contains(
                    "&"
                )
            ) {
                return id
            }
            if (id.contains("youtube.com/")) {
                if (!id.contains("channel/") && (id.startsWith("http://") || id.startsWith("https://"))) {
                    try {
                        val html = getPageContent(id, HashMap())
                        val matcher =
                            Pattern.compile("<meta itemprop=\"identifier\" content=\"([^\"]*)\"").matcher(html)
                        if (matcher.find()) {
                            return matcher.group(1)
                        }
                    } catch (ignore: IOException) {
                    }
                }
                if (id.contains("channel/")) {
                    id = id.substring(id.indexOf("channel/") + 8)
                    if (id.contains("?")) {
                        id = id.substring(0, id.indexOf("?"))
                    }
                    return id
                }
            }
            throw IllegalArgumentException(url)
        }
    }
}
