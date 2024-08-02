package com.github.kusaanko.youtubelivechat

import java.io.IOException

class ChatItem(liveChat: YouTubeLiveChat?) {
    /**
     * Get type of this item.
     *
     * @return Type of this item
     */
    @JvmField
    var type: ChatItemType

    /**
     * Get author name.
     *
     * @return Author name
     */
    @JvmField
    var authorName: String? = null

    /**
     * Get author's channel id.
     *
     * @return Author's channel id
     */
    @JvmField
    var authorChannelID: String? = null

    /**
     * Get message in String
     *
     * @return Message
     */
    @JvmField
    var message: String? = null

    /**
     * Get list of extended messages.
     * This list contains Text or Emoji.
     *
     * @return List of extended messages
     */
    @JvmField
    var messageExtended: List<Any>? = null

    /**
     * Get author's icon url.
     *
     * @return Author's icon url
     */
    @JvmField
    var authorIconURL: String? = null

    /**
     * Get id.
     *
     * @return Id
     */
    @JvmField
    var id: String? = null

    /**
     * Get timestamp of this item.
     *
     * @return Timestamp in UNIX time
     */
    @JvmField
    var timestamp: Long = 0
    @JvmField
    var authorType: MutableList<AuthorType> = ArrayList()

    /**
     * Get member badge icon url.
     * You can use if isAuthorMember() == true
     *
     * @return Member badge icon url
     */
    @JvmField
    var memberBadgeIconURL: String? = null

    /**
     * Get background color of body in int.
     *
     * @return Color in int
     */
    //For paid message
    @JvmField
    var bodyBackgroundColor: Int = 0

    /**
     * Get text color of background in int.
     *
     * @return Color in int
     */
    @JvmField
    var bodyTextColor: Int = 0

    /**
     * Get header background color in int.
     *
     * @return Color in int
     */
    @JvmField
    var headerBackgroundColor: Int = 0

    /**
     * Get header color in int.
     *
     * @return Color in int
     */
    @JvmField
    var headerTextColor: Int = 0

    /**
     * Get text color of drawing author name in int.
     *
     * @return Color in int
     */
    @JvmField
    var authorNameTextColor: Int = 0

    /**
     * Get purchase amount of this paid message
     *
     * @return Amount of money(example ￥100)
     */
    @JvmField
    var purchaseAmount: String? = null

    /**
     * Get sticker icon url.
     * You can use if getType() == PAID_STICKER
     *
     * @return Sticker icon url
     */
    //For paid sticker
    @JvmField
    var stickerIconURL: String? = null

    /**
     * Get background color in int.
     * You can use if getType() == PAID_STICKER
     *
     * @return Background color in int
     */
    @JvmField
    var backgroundColor: Int = 0

    //For ticker paid message
    @JvmField
    var endBackgroundColor: Int = 0

    /**
     * Get elapsed time from starting viewing this paid message in seconds.
     * You can use if getType() == PAID_MESSAGE
     *
     * @return Elapsed time from starting viewing this paid message in seconds
     */
    @JvmField
    var durationSec: Int = 0

    /**
     * Get full duration of paid message viewing in seconds.
     * You can use if getType() == PAID_MESSAGE
     *
     * @return Full duration of paid message viewing in seconds
     */
    @JvmField
    var fullDurationSec: Int = 0

    //If moderator enabled
    @JvmField
    var contextMenuParams: String? = null
    @JvmField
    var pinToTopParams: String? = null
    @JvmField
    var chatDeleteParams: String? = null // can be executed by author too
    @JvmField
    var timeBanParams: String? = null
    @JvmField
    var userBanParams: String? = null
    @JvmField
    var userUnbanParams: String? = null

    //Connected chat
    protected var liveChat: YouTubeLiveChat?

    @Deprecated("{@link #ChatItem(YouTubeLiveChat liveChat)}")
    protected constructor() : this(null)

    init {
        authorType.add(AuthorType.NORMAL)
        this.type = ChatItemType.MESSAGE
        this.liveChat = liveChat
    }

    /**
     * Get author types in List.
     *
     * @return List of AuthorType
     */
    fun getAuthorType(): List<AuthorType> {
        return this.authorType
    }

    val isAuthorVerified: Boolean
        /**
         * Is this message's author verified?
         *
         * @return If this message's author is verified, returns true.
         */
        get() = authorType.contains(AuthorType.VERIFIED)

    val isAuthorOwner: Boolean
        /**
         * Is this message's author owner?
         *
         * @return If this message's author is owner, returns true.
         */
        get() = authorType.contains(AuthorType.OWNER)

    val isAuthorModerator: Boolean
        /**
         * Is this message's author moderator?
         *
         * @return If this message's author is moderator, returns true.
         */
        get() = authorType.contains(AuthorType.MODERATOR)

    val isAuthorMember: Boolean
        /**
         * Is this message's author member?
         *
         * @return If this message's author is member, returns true.
         */
        get() = authorType.contains(AuthorType.MEMBER)

    override fun toString(): String {
        return "ChatItem{" +
                "type=" + type +
                ", authorName='" + authorName + '\'' +
                ", authorChannelID='" + authorChannelID + '\'' +
                ", message='" + message + '\'' +
                ", messageExtended=" + messageExtended +
                ", iconURL='" + authorIconURL + '\'' +
                ", id='" + id + '\'' +
                ", timestamp=" + timestamp +
                ", authorType=" + authorType +
                ", memberBadgeIconURL='" + memberBadgeIconURL + '\'' +
                ", bodyBackgroundColor=" + bodyBackgroundColor +
                ", bodyTextColor=" + bodyTextColor +
                ", headerBackgroundColor=" + headerBackgroundColor +
                ", headerTextColor=" + headerTextColor +
                ", authorNameTextColor=" + authorNameTextColor +
                ", purchaseAmount='" + purchaseAmount + '\'' +
                ", stickerIconURL='" + stickerIconURL + '\'' +
                ", backgroundColor=" + backgroundColor +
                ", endBackgroundColor=" + endBackgroundColor +
                ", durationSec=" + durationSec +
                ", fullDurationSec=" + fullDurationSec +
                '}'
    }

    /**
     * Delete this chat.
     * You need to set user data using setUserData() before calling this method.
     * User must be either author of chat, moderator or owner.
     *
     * @throws IOException           Http request error
     * @throws IllegalStateException The IDs are not set or permission denied error
     */
    @Throws(IOException::class)
    fun delete() {
        liveChat?.deleteMessage(this)
    }

    /**
     * Ban chat author for 300 seconds (+ delete chat).
     * You need to set user data using setUserData() before calling this method.
     * User must be either moderator or owner.
     *
     * @throws IOException           Http request error
     * @throws IllegalStateException The IDs are not set or permission denied error
     */
    @Throws(IOException::class)
    fun timeoutAuthor() {
        liveChat?.banAuthorTemporarily(this)
    }

    /**
     * Ban chat author permanently from the channel (+ delete chat).
     * You need to set user data using setUserData() before calling this method.
     * User must be either moderator or owner.
     * <br></br>
     * ****Use with cautions!!**** It is recommended to store these banned ChatItem so you can unban later.
     *
     * @throws IOException           Http request error
     * @throws IllegalStateException The IDs are not set or permission denied error
     */
    @Throws(IOException::class)
    fun banAuthor() {
        liveChat!!.banUserPermanently(this)
    }

    /**
     * Unban chat author who was permanently banned from the channel (deleted chat won't be recovered).
     * You need to set user data using setUserData() before calling this method.
     * User must be either moderator or owner.
     *
     * @throws IOException           Http request error
     * @throws IllegalStateException The IDs are not set or permission denied error
     */
    @Throws(IOException::class)
    fun unbanAuthor() {
        liveChat!!.unbanUser(this)
    }


    /**
     * Pin this chat as banner.
     * You need to set user data using setUserData() before calling this method.
     * User must be either moderator or owner.
     *
     * @throws IOException           Http request error
     * @throws IllegalStateException The IDs are not set or permission denied error
     */
    @Throws(IOException::class)
    fun pinAsBanner() {
        liveChat!!.pinMessage(this)
    }
}
