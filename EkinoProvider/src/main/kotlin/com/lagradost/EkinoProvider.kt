package com.lagradost

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.network.CloudflareKiller
import org.jsoup.Jsoup
import org.jsoup.select.Elements

class EkinoProvider : MainAPI() {
    override var mainUrl = "https://ekino-tv.pl/"
    override var name = "ekino-tv.pl"
    override var lang = "pl"
    override val hasMainPage = true
    override val usesWebView = true
    override val supportedTypes = setOf(
        TvType.TvSeries,
        TvType.Movie
    )

    private val interceptor = CloudflareKiller()

override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
    val document = app.get(mainUrl).document
    var listElements = document.select("mainWrap"); // (zmien)
    val categories = ArrayList<HomePageList>()
    for (listElement in listElements) {
        val title = capitalizeString(listElement.select("h3").text().lowercase().trim())
        val items = listElement.select(".nowa-poster").map { i ->
            val poster = i.attr("src")
            MovieSearchResponse(
                title,
                poster,
            var requestName = i.attr("name")
                TvType.Movie,
                null, // (zmien)
                0 // (zmien)
            )
        }
        categories.add(HomePageList(title, items))
    }
    return HomePageResponse(categories)
}

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/wyszukaj?phrase=$query"
        val document = app.get(url, interceptor = interceptor).document
        val lists = document.select("#advanced-search > div")

        // Zmiana w indeksach
        val movies = lists.getOrNull(1)?.select("div:not(.clearfix)") ?: Elements()
        val series = lists.getOrNull(3)?.select("div:not(.clearfix)") ?: Elements()

        fun getVideos(type: TvType, items: Elements): List<SearchResponse> {
            return items.mapNotNull { i ->
                val href = i.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                val img = i.selectFirst("a > img[src]")?.attr("src")?.replace("/thumb/", "/big/") ?: return@mapNotNull null
                val name = i.selectFirst(".title")?.text() ?: return@mapNotNull null

                if (type == TvType.TvSeries) {
                    TvSeriesSearchResponse(
                        name,
                        properUrl(href)!!,
                        this.name,
                        type,
                        properUrl(img)!!,
                        null,
                        posterHeaders = interceptor.getCookieHeaders(url).toMap()
                    )
                } else {
                    MovieSearchResponse(
                        name,
                        properUrl(href)!!,
                        this.name,
                        type,
                        properUrl(img)!!,
                        null,
                        posterHeaders = interceptor.getCookieHeaders(url).toMap()
                    )
                }
            }
        }

        return getVideos(TvType.Movie, movies) + getVideos(TvType.TvSeries, series)
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, interceptor = interceptor).document
        val documentTitle = document.select("title").text().trim()

        if (documentTitle.startsWith("Logowanie")) {
            throw RuntimeException("This page seems to be locked behind a login wall on the website, unable to scrape it. If it is not, please report it.")
        }

        var title = document.select("span[itemprop=name]").text().ifEmpty { document.select("h1").text() }
        val data = document.select("#link-list").outerHtml()
        val posterUrl = document.select("#single-poster img").attr("src")
        val plot = document.select(".description").text()
        val episodesElements = document.select("#episode-list a[href]")

        if (episodesElements.isEmpty()) {
            return MovieLoadResponse(
                title,
                properUrl(url)!!,
                name,
                TvType.Movie,
                data,
                properUrl(posterUrl)!!,
                null,
                plot
            )
        }

        title = document.selectFirst(".info")?.parent()?.select("h2")?.text() ?: title
        val episodes = episodesElements.mapNotNull { episode ->
            val e = episode.text()
            val regex = Regex("""\[s(\d{1,3})e(\d{1,3})]""").find(e) ?: return@mapNotNull null
            val eid = regex.groups

            Episode(
                episode.attr("href"),
                e.split("]")[1].trim(),
                eid[1]?.value?.toInt(),
                eid[2]?.value?.toInt(),
            )
        }.toMutableList()

        return TvSeriesLoadResponse(
            title,
            properUrl(url)!!,
            name,
            TvType.TvSeries,
            episodes,
            properUrl(posterUrl)!!,
            null,
            plot
        )
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = when {
            data.startsWith("http") -> app.get(data).document.select("#link-list").first()
            data.startsWith("URL") -> app.get(properUrl(data)!!).document.select("#link-list").first()
            else -> Jsoup.parse(data)
        }

        document?.select(".link-to-video")?.forEach { item ->
            val decoded = base64Decode(item.select("a").attr("data-iframe"))
            val link = tryParseJson<LinkElement>(decoded)?.src ?: return@forEach
            loadExtractor(link, subtitleCallback, callback)
        }
        return true
    }

    private fun properUrl(inUrl: String?): String? {
        if (inUrl == null) return null

        return fixUrl(
            inUrl.replace(
                "^URL".toRegex(),
                "/"
            )
        )
    }
}

data class LinkElement(
    @JsonProperty("src") val src: String
)
