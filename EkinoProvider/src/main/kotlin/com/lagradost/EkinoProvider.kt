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
    override var name = "Ekino.tv"
    override var lang = "pl"
    override val hasMainPage = true
    override val usesWebView = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(mainUrl).document
        val lists = document.select(".item-list")
        val categories = ArrayList<HomePageList>()
        for (list in lists) {
            val title = list.parent()?.select("h3")?.text()?.trim() ?: continue
            val items = list.select(".poster").map { item ->
                val a = item.parent() ?: return@map null
                val name = a.attr("title")
                val href = a.attr("href")
                val poster = item.select("img[src]").attr("src")
                val year = a.select(".year").text().toIntOrNull()
                MovieSearchResponse(name, href, this.name, TvType.Movie, poster, year)
            }
            categories.add(HomePageList(title, items.filterNotNull()))
        }
        return HomePageResponse(categories)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/wyszukiwarka?phrase=$query"
        val document = app.get(url).document
        val lists = document.select("#advanced-search > div")
        val movies = lists[1].select("div:not(.clearfix)")
        val series = lists[3].select("div:not(.clearfix)")

        fun getVideos(type: TvType, items: Elements): List<SearchResponse> {
            return items.mapNotNull { item ->
                val href = item.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                val img = item.selectFirst("a > img[src]")?.attr("src")?.replace("/thumb/", "/big/")
                val name = item.selectFirst(".title")?.text() ?: return@mapNotNull null
                when (type) {
                    TvType.TvSeries -> TvSeriesSearchResponse(name, href, this.name, type, img, null, null)
                    TvType.Movie -> MovieSearchResponse(name, href, this.name, type, img, null)
                    else -> null
                }
            }
        }
        return getVideos(TvType.Movie, movies) + getVideos(TvType.TvSeries, series)
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val documentTitle = document.select("title").text().trim()

        if (documentTitle.startsWith("Logowanie")) {
            throw RuntimeException("Strona jest zablokowana za pomocą zapory logowania.")
        }

        var title = document.select("span[itemprop=name]").text()
        val data = document.select("#link-list").outerHtml()
        val posterUrl = document.select("#single-poster > img").attr("src")
        val plot = document.select(".description").text()
        val episodesElements = document.select("#episode-list a[href]")
        
        if (episodesElements.isEmpty()) {
            return MovieLoadResponse(title, url, name, TvType.Movie, data, posterUrl, null, plot)
        }
        
        val episodes = episodesElements.mapNotNull { episode ->
            val e = episode.text()
            val regex = Regex("""\[s(\d{1,3})e(\d{1,3})]""").find(e) ?: return@mapNotNull null
            val eid = regex.groups
            Episode(episode.attr("href"), e.split("]")[1].trim(), eid[1]?.value?.toInt(), eid[2]?.value?.toInt())
        }.toMutableList()

        return TvSeriesLoadResponse(title, url, name, TvType.TvSeries, episodes, posterUrl, null, plot)
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = if (data.startsWith("http")) app.get(data).document.select("#link-list").first() else Jsoup.parse(data)

        document?.select(".link-to-video")?.forEach { item ->
            val decoded = base64Decode(item.select("a").attr("data-iframe"))
            val link = tryParseJson<LinkElement>(decoded)?.src ?: return@forEach
            loadExtractor(link, subtitleCallback, callback)
        }
        return true
    }
}

data class LinkElement(@JsonProperty("src") val src: String)
