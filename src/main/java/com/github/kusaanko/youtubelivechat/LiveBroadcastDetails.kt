package com.github.kusaanko.youtubelivechat

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

class LiveBroadcastDetails {
    @SerializedName("isLiveNow")
    @Expose
    var liveNow: Boolean? = null

    @SerializedName("startTimestamp")
    @Expose
    var startTimestamp: String? = null

    @SerializedName("endTimestamp")
    @Expose
    var endTimestamp: String? = null

    override fun toString(): String {
        return "LiveBroadcastDetails{" +
                "isLiveNow=" + liveNow +
                ", startTimestamp='" + startTimestamp + '\'' +
                ", endTimestamp='" + endTimestamp + '\'' +
                '}'
    }
}
