package com.lagradost

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import org.jsoup.select.Elements

open class EkinoProvider : MainAPI() {
    override var mainUrl = "https://ekino-tv.pl/"
    override var name = "ekino-tv.pl"
    override var lang = "pl"
    override val hasMainPage = true
    override val usesWebView = true
    override val supportedTypes = setOf(
        TvType.TvSeries,
        TvType.Movie
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(mainUrl).document
        val lists = document.select(".list")
        val categories = ArrayList<HomePageList>()
        for (list in lists) {
            val title = list.parent()?.selectFirst("h4")?.text()?.capitalize() ?: "Kategoria"
            val items = list.select(".scope_left").mapNotNull { item ->
                val parent = item.parent()
                val name = parent?.selectFirst(".title")?.text() ?: return@mapNotNull null
                val href = parent.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                val poster = item.selectFirst("img[src]")?.attr("src")?.let { fixUrl(it) } ?: ""
                val year = parent.selectFirst(".cates")?.text()?.toIntOrNull()
                MovieSearchResponse(
                    name,
                    fixUrl(href),
                    this.name,
                    TvType.Movie,
                    poster,
                    year
                )
            }
            categories.add(HomePageList(title, items))
        }
        return HomePageResponse(categories)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/wyszukiwarka?phrase=$query" // Corrected URL parameter
        val document = app.get(url).document
        val results = document.select(".scope_left")
        return results.mapNotNull { item ->
            val name = item.selectFirst(".title")?.text() ?: return@mapNotNull null
            val href = item.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val poster = item.selectFirst("img[src]")?.attr("src") ?: ""
            MovieSearchResponse(name, fixUrl(href), this.name, TvType.Movie, poster)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = if (data.startsWith("http"))
            app.get(data).document.selectFirst("#link-list")
        else Jsoup.parse(data)

        document?.select(".link-to-video")?.forEach { item ->
            val decoded = base64Decode(item.selectFirst("a")?.attr("data-iframe") ?: return@forEach)
            val link = tryParseJson<LinkElement>(decoded)?.src ?: return@forEach
            loadExtractor(link, subtitleCallback, callback)
        }
        return true
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        // ... (rest of your load() function logic)
    }
}
