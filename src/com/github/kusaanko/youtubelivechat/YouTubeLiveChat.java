package com.github.kusaanko.youtubelivechat;

import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("unchecked")
public class YouTubeLiveChat {
    /**
     * This is user agent used by YouTubeLiveChat.
     * You can edit this.
     */
    public static String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.190 Safari/537.36,gzip(gfe)";

    private static final String liveChatApi = "https://www.youtube.com/youtubei/v1/live_chat/get_live_chat?key=";
    private static final String liveChatReplayApi = "https://www.youtube.com/youtubei/v1/live_chat/get_live_chat_replay?key=";
    private static final String liveChatSendMessageApi = "https://www.youtube.com/youtubei/v1/live_chat/send_message?key=";

    private String videoId;
    private String channelId;
    private String continuation;
    private boolean isReplay;
    private final boolean isTopChatOnly;
    private String visitorData;
    private ChatItem bannerItem;
    private final ArrayList<ChatItem> chatItems;
    private final ArrayList<ChatItem> chatItemTickerPaidMessages;
    private final ArrayList<ChatItemDelete> chatItemDeletes;
    private Locale locale;
    private String clientVersion;
    private boolean isInitDataAvailable;
    private String apiKey;
    private String datasyncId;
    private int commentCounter;
    private String clientMessageId;
    private String params;
    private String SAPISID, HSID, SSID, APISID, SID;

    private MessageDigest sha1;

    /**
     * Initialize YouTubeLiveChat
     *
     * @param id            Id used in YouTube
     * @param isTopChatOnly Is this top chat only mode
     * @param type          The type of id (VIDEO or CHANNEL)
     * @throws IOException              Http request error
     * @throws IllegalArgumentException Video id is incorrect
     */
    public YouTubeLiveChat(String id, boolean isTopChatOnly, IdType type) throws IOException {
        this.isTopChatOnly = isTopChatOnly;
        this.visitorData = "";
        this.chatItems = new ArrayList<>();
        this.chatItemTickerPaidMessages = new ArrayList<>();
        this.chatItemDeletes = new ArrayList<>();
        this.locale = Locale.US;
        this.commentCounter = 0;
        this.clientMessageId = Util.generateClientMessageId();
        try {
            this.getInitialData(id, type);
        } catch (IOException exception) {
            throw new IOException(exception.getLocalizedMessage());
        }
        if (this.continuation == null) {
            throw new IllegalArgumentException("Invalid " + type.toString().toLowerCase() + " id:" + id);
        }
    }

    /**
     * Initialize YouTubeLiveChat using video id
     *
     * @param videoId       Video id used in YouTube
     * @param isTopChatOnly Is this top chat only mode
     * @throws IOException              Http request error
     * @throws IllegalArgumentException Video id is incorrect
     */
    public YouTubeLiveChat(String videoId, boolean isTopChatOnly) throws IOException {
        this(videoId, isTopChatOnly, IdType.VIDEO);
    }

    /**
     * Initialize YouTubeLiveChat using video id.
     * This works with top chat only mode.
     *
     * @param videoId Video id used in YouTube
     * @throws IOException              Http request error
     * @throws IllegalArgumentException Video id is incorrect
     */
    public YouTubeLiveChat(String videoId) throws IOException {
        this(videoId, true, IdType.VIDEO);
    }

    /**
     * Reset this. If you have an error, try to call this.
     * You don't need to call setLocale() again.
     *
     * @throws IOException Http request error
     */
    public void reset() throws IOException {
        this.visitorData = "";
        this.chatItems.clear();
        this.chatItemTickerPaidMessages.clear();
        this.chatItemDeletes.clear();
        this.commentCounter = 0;
        this.clientMessageId = Util.generateClientMessageId();
        try {
            this.getInitialData(this.videoId, IdType.VIDEO);
        } catch (IOException exception) {
            throw new IOException(exception.getLocalizedMessage());
        }
    }

    /**
     * Update chat data
     *
     * @throws IOException Http request error
     */
    public void update() throws IOException {
        this.update(0);
    }

    /**
     * Update chat data with offset
     *
     * @param offsetInMs Offset in milli seconds
     * @throws IOException Http request error
     */
    public void update(long offsetInMs) throws IOException {
        if (this.isInitDataAvailable) {
            this.isInitDataAvailable = false;
            return;
        }
        this.chatItems.clear();
        this.chatItemTickerPaidMessages.clear();
        this.chatItemDeletes.clear();
        try {
            //Get live actions
            if (this.continuation == null) {
                throw new IOException("continuation is null! Please call reset().");
            }
            String pageContent = Util.getPageContentWithJson((this.isReplay ? liveChatReplayApi : liveChatApi) + this.apiKey, this.getPayload(offsetInMs), this.getHeader());
            Map<String, Object> json = Util.toJSON(pageContent);
            if (this.visitorData == null || this.visitorData.isEmpty()) {
                this.visitorData = Util.getJSONValueString(Util.getJSONMap(json, "responseContext"), "visitorData");
            }
            //Get clientVersion
            List<Object> serviceTrackingParams = Util.getJSONList(json, "serviceTrackingParams", "responseContext");
            if (serviceTrackingParams != null) {
                for (Object ser : serviceTrackingParams) {
                    Map<String, Object> service = (Map<String, Object>) ser;
                    String serviceName = Util.getJSONValueString(service, "service");
                    if (serviceName != null && serviceName.equals("CSI")) {
                        List<Object> params = Util.getJSONList(service, "params");
                        if (params != null) {
                            for (Object par : params) {
                                Map<String, Object> param = (Map<String, Object>) par;
                                String key = Util.getJSONValueString(param, "key");
                                if (key != null && key.equals("cver")) {
                                    this.clientVersion = Util.getJSONValueString(param, "value");
                                }
                            }
                        }
                    }
                }
            }
            //Parse actions and update continuation
            Map<String, Object> liveChatContinuation = Util.getJSONMap(json, "continuationContents", "liveChatContinuation");
            if (this.isReplay) {
                if (liveChatContinuation != null) {
                    List<Object> actions = Util.getJSONList(liveChatContinuation, "actions");
                    if (actions != null) {
                        this.parseActions(actions);
                    }
                }
                List<Object> continuations = Util.getJSONList(liveChatContinuation, "continuations");
                //Update continuation
                if (continuations != null) {
                    for (Object co : continuations) {
                        Map<String, Object> continuation = (Map<String, Object>) co;
                        String value = Util.getJSONValueString(Util.getJSONMap(continuation, "liveChatReplayContinuationData"), "continuation");
                        if (value != null) {
                            this.continuation = value;
                        }
                    }
                }
            } else {
                if (liveChatContinuation != null) {
                    List<Object> actions = Util.getJSONList(liveChatContinuation, "actions");
                    if (actions != null) {
                        this.parseActions(actions);
                    }
                    List<Object> continuations = Util.getJSONList(liveChatContinuation, "continuations");
                    if (continuations != null) {
                        for (Object co : continuations) {
                            Map<String, Object> continuation = (Map<String, Object>) co;
                            this.continuation = Util.getJSONValueString(Util.getJSONMap(continuation, "invalidationContinuationData"), "continuation");
                            if (this.continuation == null) {
                                this.continuation = Util.getJSONValueString(Util.getJSONMap(continuation, "timedContinuationData"), "continuation");
                            }
                            if (this.continuation == null) {
                                this.continuation = Util.getJSONValueString(Util.getJSONMap(continuation, "reloadContinuationData"), "continuation");
                            }
                        }
                    }
                }
            }
        } catch (IOException exception) {
            throw new IOException("Can't get youtube live chat!");
        }
    }


    /**
     * Send chat message
     * You need to set user data using setUserData() before calling this method
     *
     * @param message Chat message to send
     * @throws IOException           Http request error
     * @throws IllegalStateException The IDs are not set error
     */
    public void sendMessage(String message) throws IOException, IllegalStateException {
        if(this.isReplay) {
            throw new IllegalStateException("This live is replay! You can send a message if this live isn't replay.");
        }
        if (this.SAPISID == null || this.HSID == null || this.SSID == null || this.APISID == null || this.SID == null) {
            throw new IllegalStateException("You need to set user data using setUserData()");
        }

        try {
            if (this.datasyncId == null) {
                throw new IOException("datasyncId is null! Please call reset() or set user data.");
            }
            Util.sendHttpRequestWithJson(liveChatSendMessageApi + this.apiKey, this.getPayloadToSendMessage(message), this.getHeader());
        } catch (IOException exception) {
            throw new IOException("Couldn't send a message!");
        }
    }


    /**
     * Language used to get chat
     * Default locale is Locale.US(en_US)
     * setLocale(Locale.US);
     *
     * @param locale Language (country code and language code are required)
     */
    public void setLocale(Locale locale) {
        if (locale.getCountry() == null || locale.getCountry().isEmpty()) {
            throw new IllegalArgumentException("Locale must be set country!");
        }
        if (locale.getLanguage() == null || locale.getLanguage().isEmpty()) {
            throw new IllegalArgumentException("Locale must be set language!");
        }
        this.locale = locale;
    }

    /**
     * Set user data
     * The IDs are written in your browser's Cookie
     *
     * @param SAPISID SAPIID
     * @param HSID    HSID
     * @param SSID    SSID
     * @param APISID  APISID
     * @param SID     SID
     */
    public void setUserData(String SAPISID, String HSID, String SSID, String APISID, String SID) {
        this.SAPISID = SAPISID;
        this.HSID = HSID;
        this.SSID = SSID;
        this.APISID = APISID;
        this.SID = SID;

        try {
            this.reset();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void parseActions(List<Object> json) {
        for (Object i : json) {
            Map<String, Object> actions = (Map<String, Object>) i;
            Map<String, Object> addChatItemAction = Util.getJSONMap(actions, "addChatItemAction");
            //For replay
            if (addChatItemAction == null) {
                Map<String, Object> replayChatItemAction = Util.getJSONMap(actions, "replayChatItemAction");
                if (replayChatItemAction != null) {
                    List<Object> acts = Util.getJSONList(replayChatItemAction, "actions");
                    if (acts != null) {
                        parseActions(acts);
                    }
                }
            }
            if (addChatItemAction != null) {
                ChatItem chatItem = null;
                Map<String, Object> item = Util.getJSONMap(addChatItemAction, "item");
                if (item != null) {
                    chatItem = new ChatItem();
                    this.parseChatItem(chatItem, item);
                }
                if (chatItem != null && chatItem.id != null) {
                    this.chatItems.add(chatItem);
                }
            }
            //Pinned message
            Map<String, Object> contents = Util.getJSONMap(actions, "addBannerToLiveChatCommand", "bannerRenderer", "liveChatBannerRenderer", "contents");
            if (contents != null) {
                ChatItem chatItem = new ChatItem();
                this.parseChatItem(chatItem, contents);
                this.bannerItem = chatItem;
            }
            Map<String, Object> markChatItemAsDeletedAction = Util.getJSONMap(actions, "markChatItemAsDeletedAction");
            if (markChatItemAsDeletedAction != null) {
                ChatItemDelete chatItemDelete = new ChatItemDelete();
                chatItemDelete.message = this.parseMessage(Util.getJSONMap(markChatItemAsDeletedAction, "deletedStateMessage"), new ArrayList<>());
                chatItemDelete.targetId = Util.getJSONValueString(markChatItemAsDeletedAction, "targetItemId");
                this.chatItemDeletes.add(chatItemDelete);
            }
        }
    }

    private void parseChatItem(ChatItem chatItem, Map<String, Object> action) {
        Map<String, Object> liveChatTextMessageRenderer = Util.getJSONMap(action, "liveChatTextMessageRenderer");
        Map<String, Object> liveChatPaidMessageRenderer = Util.getJSONMap(action, "liveChatPaidMessageRenderer");
        Map<String, Object> liveChatPaidStickerRenderer = Util.getJSONMap(action, "liveChatPaidStickerRenderer");
        Map<String, Object> liveChatMembershipItemRenderer = Util.getJSONMap(action, "liveChatMembershipItemRenderer");
        if (liveChatTextMessageRenderer == null && liveChatPaidMessageRenderer != null) {
            liveChatTextMessageRenderer = liveChatPaidMessageRenderer;
        }
        if (liveChatTextMessageRenderer == null && liveChatPaidStickerRenderer != null) {
            liveChatTextMessageRenderer = liveChatPaidStickerRenderer;
        }
        if (liveChatTextMessageRenderer == null && liveChatMembershipItemRenderer != null) {
            liveChatTextMessageRenderer = liveChatMembershipItemRenderer;
        }
        if (liveChatTextMessageRenderer != null) {
            chatItem.authorName = Util.getJSONValueString(Util.getJSONMap(liveChatTextMessageRenderer, "authorName"), "simpleText");
            chatItem.id = Util.getJSONValueString(liveChatTextMessageRenderer, "id");
            chatItem.authorChannelID = Util.getJSONValueString(liveChatTextMessageRenderer, "authorExternalChannelId");
            Map<String, Object> message = Util.getJSONMap(liveChatTextMessageRenderer, "message");
            chatItem.messageExtended = new ArrayList<>();
            chatItem.message = parseMessage(message, chatItem.messageExtended);
            List<Object> authorPhotoThumbnails = Util.getJSONList(liveChatTextMessageRenderer, "thumbnails", "authorPhoto");
            if (authorPhotoThumbnails != null) {
                chatItem.authorIconURL = this.getJSONThumbnailURL(authorPhotoThumbnails);
            }
            String timestampStr = Util.getJSONValueString(liveChatTextMessageRenderer, "timestampUsec");
            if (timestampStr != null) {
                chatItem.timestamp = Long.parseLong(timestampStr);
            }
            List<Object> authorBadges = Util.getJSONList(liveChatTextMessageRenderer, "authorBadges");
            if (authorBadges != null) {
                for (Object au : authorBadges) {
                    Map<String, Object> authorBadge = (Map<String, Object>) au;
                    Map<String, Object> liveChatAuthorBadgeRenderer = Util.getJSONMap(authorBadge, "liveChatAuthorBadgeRenderer");
                    if (liveChatAuthorBadgeRenderer != null) {
                        String type = Util.getJSONValueString(Util.getJSONMap(liveChatAuthorBadgeRenderer, "icon"), "iconType");
                        if (type != null) {
                            switch (type) {
                                case "VERIFIED":
                                    chatItem.authorType.add(AuthorType.VERIFIED);
                                    break;
                                case "OWNER":
                                    chatItem.authorType.add(AuthorType.OWNER);
                                    break;
                                case "MODERATOR":
                                    chatItem.authorType.add(AuthorType.MODERATOR);
                                    break;
                            }
                        }
                        Map<String, Object> customThumbnail = Util.getJSONMap(liveChatAuthorBadgeRenderer, "customThumbnail");
                        if (customThumbnail != null) {
                            chatItem.authorType.add(AuthorType.MEMBER);
                            List<Object> thumbnails = (List<Object>) customThumbnail.get("thumbnails");
                            chatItem.memberBadgeIconURL = this.getJSONThumbnailURL(thumbnails);
                        }
                    }
                }
            }
        }
        if (action.containsKey("liveChatViewerEngagementMessageRenderer")) {
            Map<String, Object> liveChatViewerEngagementMessageRenderer = (Map<String, Object>) action.get("liveChatViewerEngagementMessageRenderer");
            chatItem.authorName = "YouTube";
            chatItem.authorChannelID = "user/YouTube";
            chatItem.authorType.add(AuthorType.YOUTUBE);
            chatItem.id = Util.getJSONValueString(liveChatViewerEngagementMessageRenderer, "id");
            chatItem.messageExtended = new ArrayList<>();
            chatItem.message = this.parseMessage(Util.getJSONMap(liveChatViewerEngagementMessageRenderer, "message"), chatItem.messageExtended);
            String timestampStr = Util.getJSONValueString(liveChatViewerEngagementMessageRenderer, "timestampUsec");
            if (timestampStr != null) {
                chatItem.timestamp = Long.parseLong(timestampStr);
            }
        }
        if (liveChatPaidMessageRenderer != null) {
            chatItem.bodyBackgroundColor = Util.getJSONValueInt(liveChatPaidMessageRenderer, "bodyBackgroundColor");
            chatItem.bodyTextColor = Util.getJSONValueInt(liveChatPaidMessageRenderer, "bodyBackgroundColor");
            chatItem.headerBackgroundColor = Util.getJSONValueInt(liveChatPaidMessageRenderer, "bodyBackgroundColor");
            chatItem.headerTextColor = Util.getJSONValueInt(liveChatPaidMessageRenderer, "bodyBackgroundColor");
            chatItem.authorNameTextColor = Util.getJSONValueInt(liveChatPaidMessageRenderer, "authorNameTextColor");
            chatItem.purchaseAmount = Util.getJSONValueString(Util.getJSONMap(liveChatPaidMessageRenderer, "purchaseAmountText"), "simpleText");
            chatItem.type = ChatItemType.PAID_MESSAGE;
        }
        if (liveChatPaidStickerRenderer != null) {
            chatItem.backgroundColor = Util.getJSONValueInt(liveChatPaidStickerRenderer, "backgroundColor");
            chatItem.purchaseAmount = Util.getJSONValueString(Util.getJSONMap(liveChatPaidStickerRenderer, "purchaseAmountText"), "simpleText");
            List<Object> thumbnails = Util.getJSONList(liveChatPaidStickerRenderer, "thumbnails", "sticker");
            if (thumbnails != null) {
                chatItem.stickerIconURL = this.getJSONThumbnailURL(thumbnails);
            }
            chatItem.type = ChatItemType.PAID_STICKER;
        }
        Map<String, Object> liveChatTickerPaidMessageItemRenderer = Util.getJSONMap(action, "liveChatTickerPaidMessageItemRenderer");
        if (liveChatTickerPaidMessageItemRenderer != null) {
            Map<String, Object> renderer = Util.getJSONMap(liveChatPaidMessageRenderer, "showItemEndpoint", "showLiveChatItemEndpoint", "renderer");
            this.parseChatItem(chatItem, renderer);
            chatItem.endBackgroundColor = Util.getJSONValueInt(liveChatPaidMessageRenderer, "endBackgroundColor");
            chatItem.durationSec = Util.getJSONValueInt(liveChatPaidMessageRenderer, "durationSec");
            chatItem.fullDurationSec = Util.getJSONValueInt(liveChatPaidMessageRenderer, "fullDurationSec");
            chatItem.type = ChatItemType.TICKER_PAID_MESSAGE;
        }
        if (liveChatMembershipItemRenderer != null) {
            chatItem.messageExtended = new ArrayList<>();
            chatItem.message = this.parseMessage(Util.getJSONMap(liveChatMembershipItemRenderer, "headerSubtext"), chatItem.messageExtended);
            chatItem.type = ChatItemType.NEW_MEMBER_MESSAGE;
        }
    }

    private String getJSONThumbnailURL(List<Object> thumbnails) {
        long size = 0;
        String url = null;
        for (Object tObj : thumbnails) {
            Map<String, Object> thumbnail = (Map<String, Object>) tObj;
            long width = Util.getJSONValueLong(thumbnail, "width");
            String u = Util.getJSONValueString(thumbnail, "url");
            if (u != null) {
                if (size <= width) {
                    size = width;
                    url = u;
                }
            }
        }
        return url;
    }

    private String parseMessage(Map<String, Object> message, List<Object> messageExtended) {
        StringBuilder text = new StringBuilder();
        List<Object> runs = Util.getJSONList(message, "runs");
        if (runs != null) {
            text = new StringBuilder();
            for (Object runObj : runs) {
                Map<String, Object> run = (Map<String, Object>) runObj;
                if (run.containsKey("text")) {
                    text.append(run.get("text").toString());
                    messageExtended.add(new Text(run.get("text").toString()));
                }
                Map<String, Object> emojiMap = Util.getJSONMap(run, "emoji");
                if (emojiMap != null) {
                    Emoji emoji = new Emoji();
                    emoji.emojiId = Util.getJSONValueString(emojiMap, "emojiId");
                    List<Object> shortcutsList = Util.getJSONList(emojiMap, "shortcuts");
                    ArrayList<String> shortcuts = new ArrayList<>();
                    if (shortcutsList != null) {
                        for (Object s : shortcutsList) {
                            shortcuts.add(s.toString());
                        }
                    }
                    emoji.shortcuts = shortcuts;
                    text.append(" ").append(shortcuts.get(0)).append(" ");
                    List<Object> searchTermsList = Util.getJSONList(emojiMap, "searchTerms");
                    ArrayList<String> searchTerms = new ArrayList<>();
                    if (searchTermsList != null) {
                        for (Object s : searchTermsList) {
                            searchTerms.add(s.toString());
                        }
                    }
                    emoji.searchTerms = searchTerms;
                    List<Object> thumbnails = Util.getJSONList(emojiMap, "thumbnails", "image");
                    if (thumbnails != null) {
                        emoji.iconURL = this.getJSONThumbnailURL(thumbnails);
                    }
                    emoji.isCustomEmoji = Util.getJSONValueBoolean(emojiMap, "isCustomEmoji");
                    messageExtended.add(emoji);
                }
            }
        }
        return text.length() == 0 ? null : text.toString();
    }

    /**
     * Get video id
     *
     * @return Video id
     */
    public String getVideoId() {
        return this.videoId;
    }

    /**
     * Get channel id of this live.
     *
     * @return Channel id
     */
    public String getChannelId() {
        return this.channelId;
    }

    /**
     * Check this live replay is replay.
     *
     * @return If this live is replay, returns true.
     */
    public boolean isReplay() {
        return this.isReplay;
    }

    /**
     * Get pinned message
     *
     * @return ChatItem
     */
    public ChatItem getBannerItem() {
        return this.bannerItem;
    }

    /**
     * Get list of ChatItem
     *
     * @return List of ChatItem
     */
    public ArrayList<ChatItem> getChatItems() {
        return (ArrayList<ChatItem>) this.chatItems.clone();
    }

    /**
     * Get list of ChatItemDelete
     *
     * @return List of ChatItemDelete
     */
    public ArrayList<ChatItemDelete> getChatItemDeletes() {
        return (ArrayList<ChatItemDelete>) this.chatItemDeletes.clone();
    }

    /**
     * Get list of ChatItem(type=TICKER_PAID_MESSAGE)
     *
     * @return List of ChatItem
     */
    public ArrayList<ChatItem> getChatTickerPaidMessages() {
        return (ArrayList<ChatItem>) this.chatItemTickerPaidMessages.clone();
    }

    private void getInitialData(String id, IdType type) throws IOException {
        this.isInitDataAvailable = true;
        {
            String html = "";
            if (type == IdType.VIDEO) {
                this.videoId = id;
                html = Util.getPageContent("https://www.youtube.com/watch?v=" + id, getHeader());
                Matcher channelIdMatcher = Pattern.compile("\"channelId\":\"([^\"]*)\",\"isOwnerViewing\"").matcher(html);
                if (channelIdMatcher.find()) {
                    this.channelId = channelIdMatcher.group(1);
                }
            } else if (type == IdType.CHANNEL) {
                this.channelId = id;
                html = Util.getPageContent("https://www.youtube.com/channel/" + id + "/live", getHeader());
                Matcher videoIdMatcher = Pattern.compile("\"updatedMetadataEndpoint\":\\{\"videoId\":\"([^\"]*)").matcher(html);
                if (videoIdMatcher.find()) {
                    this.videoId = videoIdMatcher.group(1);
                } else {
                    throw new IOException("The channel (ID:" + this.channelId + ") has not started live streaming!");
                }
            }
            Matcher isReplayMatcher = Pattern.compile("\"isReplay\":([^,]*)").matcher(html);
            if (isReplayMatcher.find()) {
                this.isReplay = Boolean.parseBoolean(isReplayMatcher.group(1));
            }
            Matcher topOnlyContinuationMatcher = Pattern.compile("\"selected\":true,\"continuation\":\\{\"reloadContinuationData\":\\{\"continuation\":\"([^\"]*)").matcher(html);
            if (topOnlyContinuationMatcher.find()) {
                this.continuation = topOnlyContinuationMatcher.group(1);
            }
            if (!this.isTopChatOnly) {
                Matcher allContinuationMatcher = Pattern.compile("\"selected\":false,\"continuation\":\\{\"reloadContinuationData\":\\{\"continuation\":\"([^\"]*)").matcher(html);
                if (allContinuationMatcher.find()) {
                    this.continuation = allContinuationMatcher.group(1);
                }
            }
            Matcher innertubeApiKeyMatcher = Pattern.compile("\"innertubeApiKey\":\"([^\"]*)\"").matcher(html);
            if (innertubeApiKeyMatcher.find()) {
                this.apiKey = innertubeApiKeyMatcher.group(1);
            }
            Matcher datasyncIdMatcher = Pattern.compile("\"datasyncId\":\"([^|]*)\\|\\|.*\"").matcher(html);
            if (datasyncIdMatcher.find()) {
                this.datasyncId = datasyncIdMatcher.group(1);
            }
            if (this.isReplay) {
                html = Util.getPageContent("https://www.youtube.com/live_chat_replay?continuation=" + this.continuation + "", new HashMap<>());
                String initJson = html.substring(html.indexOf("window[\"ytInitialData\"] = ") + "window[\"ytInitialData\"] = ".length());
                initJson = initJson.substring(0, initJson.indexOf(";</script>"));
                Map<String, Object> json = Util.toJSON(initJson);
                Map<String, Object> timedContinuationData = Util.getJSONMap(json, "continuationContents", "liveChatContinuation", "continuations", 0, "liveChatReplayContinuationData");
                if (timedContinuationData != null) {
                    this.continuation = timedContinuationData.get("continuation").toString();
                }
                List<Object> actions = Util.getJSONList(json, "actions", "continuationContents", "liveChatContinuation");
                if (actions != null) {
                    this.parseActions(actions);
                }
            } else {
                html = Util.getPageContent("https://www.youtube.com/live_chat?continuation=" + this.continuation + "", getHeader());
                String initJson = html.substring(html.indexOf("window[\"ytInitialData\"] = ") + "window[\"ytInitialData\"] = ".length());
                initJson = initJson.substring(0, initJson.indexOf(";</script>"));
                Map<String, Object> json = Util.toJSON(initJson);
                Map<String, Object> sendLiveChatMessageEndpoint = Util.getJSONMap(json, "continuationContents", "liveChatContinuation", "actionPanel", "liveChatMessageInputRenderer", "sendButton", "buttonRenderer", "serviceEndpoint", "sendLiveChatMessageEndpoint");
                if (sendLiveChatMessageEndpoint != null) {
                    this.params = sendLiveChatMessageEndpoint.get("params").toString();
                }
                this.isInitDataAvailable = false;
            }
        }
    }

    private String getClientVersion() {
        if (this.clientVersion != null) {
            return this.clientVersion;
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
        return "2." + format.format(new Date(System.currentTimeMillis() - (24 * 60 * 1000))) + ".06.00";
    }

    private String getPayload(long offsetInMs) {
        if (offsetInMs < 0) {
            offsetInMs = 0;
        }
        Map<String, Object> json = new LinkedHashMap<>();
        Map<String, Object> context = new LinkedHashMap<>();
        Map<String, Object> client = new LinkedHashMap<>();
        json.put("context", context);
        context.put("client", client);
        client.put("visitorData", this.visitorData);
        client.put("userAgent", userAgent);
        client.put("clientName", "WEB");
        client.put("clientVersion", this.getClientVersion());
        client.put("gl", this.locale.getCountry());
        client.put("hl", this.locale.getLanguage());
        json.put("continuation", this.continuation);
        if (this.isReplay) {
            LinkedHashMap<String, Object> state = new LinkedHashMap<>();
            state.put("playerOffsetMs", String.valueOf(offsetInMs));
            json.put("currentPlayerState", state);
        }

        return Util.toJSON(json);
    }

    private String getPayloadToSendMessage(String message) {
        Map<String, Object> json = new LinkedHashMap<>();
        Map<String, Object> context = new LinkedHashMap<>();
        Map<String, Object> user = new LinkedHashMap<>();
        Map<String, Object> richMessage = new LinkedHashMap<>();
        Map<String, Object> textSegments = new LinkedHashMap<>();
        Map<String, Object> client = new LinkedHashMap<>();
        json.put("clientMessageId", clientMessageId + commentCounter++);
        json.put("context", context);
        context.put("client", client);
        client.put("clientName", "WEB");
        client.put("clientVersion", this.getClientVersion());
        context.put("user", user);
        user.put("onBehalfOfUser", datasyncId);
        json.put("params", this.params);
        json.put("richMessage", richMessage);
        richMessage.put("textSegments", textSegments);
        textSegments.put("text", message);

        return Util.toJSON(json);
    }

    private Map<String, String> getHeader() {
        HashMap<String, String> header = new HashMap<>();
        if (this.SAPISID == null || this.HSID == null || this.SSID == null || this.APISID == null || this.SID == null) return header;
        String time = System.currentTimeMillis() / 1000 + "";
        String origin = "https://www.youtube.com";
        String hash = time + " " + this.SAPISID + " " + origin;
        byte[] sha1_result = this.getSHA1Engine().digest(hash.getBytes());

        header.put("Authorization", "SAPISIDHASH " + time + "_" + String.format("%040x", new BigInteger(1, sha1_result)));
        header.put("X-Origin", origin);
        header.put("Origin", origin);
        header.put("Cookie", String.format("SAPISID=%s; HSID=%s; SSID=%s; APISID=%s; SID=%s;", this.SAPISID, this.HSID, this.SSID, this.APISID, this.SID));

        return header;
    }

    private MessageDigest getSHA1Engine() {
        if (this.sha1 == null) {
            try {
                this.sha1 = MessageDigest.getInstance("SHA-1");
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
        }
        return this.sha1;
    }

    /**
     * Get video id from url
     *
     * @param url Full url(example https://www.youtube.com/watch?v=Aw5b1sa0w)
     * @return Video id
     * @throws IllegalArgumentException URL format is incorrect
     */
    public static String getVideoIdFromURL(String url) {
        String id = url;
        if (!id.contains("?") && !id.contains(".com/") && !id.contains(".be/") && !id.contains("/") && !id.contains("&")) {
            return id;
        }
        if (id.contains("youtube.com/watch?")) {
            while (id.contains("v=")) {
                id = id.substring(id.indexOf("v=") - 1);
                if (id.startsWith("?") || id.startsWith("&")) {
                    id = id.substring(3);
                    if (id.contains("&")) {
                        id = id.substring(0, id.indexOf("&"));
                    }
                    if (!id.contains("?")) {
                        return id;
                    }
                } else {
                    id = id.substring(3);
                }
            }
        }
        if (id.contains("youtube.com/embed/")) {
            id = id.substring(id.indexOf("embed/") + 6);
            if (id.contains("?")) {
                id = id.substring(0, id.indexOf("?"));
            }
            return id;
        }
        if (id.contains("youtu.be/")) {
            id = id.substring(id.indexOf("youtu.be/") + 9);
            if (id.contains("?")) {
                id = id.substring(0, id.indexOf("?"));
            }
            return id;
        }
        throw new IllegalArgumentException(url);
    }

    /**
     * Get channel id from url
     *
     * @param url Full url(example https://www.youtube.com/channel/USWmbkAWEKOG43WAnbw)
     * @return Channel id
     * @throws IllegalArgumentException URL format is incorrect
     */
    public static String getChannelIdFromURL(String url) {
        String id = url;
        if (!id.contains("?") && !id.contains(".com/") && !id.contains(".be/") && !id.contains("/") && !id.contains("&")) {
            return id;
        }
        if (id.contains("youtube.com/")) {
            if (!id.contains("channel/") && (id.startsWith("http://") || id.startsWith("https://"))) {
                try {
                    String html = Util.getPageContent(id, new HashMap<>());
                    Matcher matcher = Pattern.compile("<meta itemprop=\"channelId\" content=\"([^\"]*)\"").matcher(html);
                    if (matcher.find()) {
                        return matcher.group(1);
                    }
                } catch (IOException ignore) {
                }
            }
            if (id.contains("channel/")) {
                id = id.substring(id.indexOf("channel/") + 8);
                if (id.contains("?")) {
                    id = id.substring(0, id.indexOf("?"));
                }
                return id;
            }
        }
        throw new IllegalArgumentException(url);
    }
}
