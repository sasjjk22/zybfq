package com.tjjiangh.bofangqi.subscription

import java.io.Serializable

data class CategoryItem(
    val typeId: String,
    val typeName: String
) : Serializable

data class VodItem(
    val vodId: String,
    val vodName: String,
    val vodPic: String,
    val vodRemarks: String = "",
    val typeName: String = ""
) : Serializable {
    val picUrl: String get() = vodPic
    val title: String get() = vodName
}

data class BannerItem(
    val vodId: String,
    val title: String,
    val picUrl: String,
    val remarks: String
) : Serializable

data class EpisodeItem(
    val name: String,
    val url: String
) : Serializable

data class PlayRoute(
    val routeName: String,
    val episodes: List<EpisodeItem>
) : Serializable

data class VodDetail(
    val vodId: String,
    val vodName: String,
    val vodPic: String,
    val vodRemarks: String,
    val typeName: String,
    val vodYear: String = "",
    val vodArea: String = "",
    val vodActor: String = "",
    val vodDirector: String = "",
    val vodContent: String = "",
    val routes: List<PlayRoute> = emptyList()
) : Serializable