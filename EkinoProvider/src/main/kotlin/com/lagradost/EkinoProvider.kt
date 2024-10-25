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
        val lists = document.select(".mostPopular .list")
        val categories = ArrayList<HomePageList>()

        val title = "Gorące Filmy!"
        val items = lists.select("li").map { item ->
            val name = item.select(".scope_right .title a").text()
            val href = mainUrl + item.select(".scope_right .title a").attr("href")
            val poster = item.select(".scope_left img[src]").attr("src")
            val year = item.select(".info-categories .cates").text().substringBefore("|").trim().toIntOrNull() 
            val description = item.select(".movieDesc").text() // Dodane

            MovieSearchResponse(
                name,
                href,
                this.name,
                TvType.Movie,
                poster,
                year,
                description // Dodajemy opis
            )
        }
        categories.add(HomePageList(title, items))
        return HomePageResponse(categories)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/wyszukiwarka?phrase=$query"
        val document = app.get(url).document
        val lists = document.select("#advanced-search > div")
        val movies = lists[1].select(".movie") 
        val series = lists[3].select(".tv-series") 

        if (movies.isEmpty() && series.isEmpty()) return emptyList()

        fun getVideos(type: TvType, items: Elements): List<SearchResponse> {
            return items.mapNotNull { i ->
                val href = i.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                val img = i.selectFirst("a > img[src]")?.attr("src")?.replace("/thumb/", "/big/")
                val name = i.selectFirst(".title")?.text() ?: return@mapNotNull null

                if (type == TvType.TvSeries) {
                    TvSeriesSearchResponse(
                        name,
                        href,
                        this.name,
                        type,
                        img,
                        null,
                        null
                    )
                } else {
                    MovieSearchResponse(name, href, this.name, type, img, null, null) // Dodaj opis w razie potrzeby
                }
            }
        }

        return getVideos(TvType.Movie, movies) + getVideos(TvType.TvSeries, series)
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val documentTitle = document.select("title").text().trim()

        if (documentTitle.startsWith("Logowanie")) {
            throw RuntimeException("Ta strona wydaje się być zablokowana za ścianą logowania, nie można jej zeskrobać.")
        }

        var title = document.select("span[itemprop=name]").text()
        val data = document.select("#link-list").outerHtml()
        val posterUrl = document.select("#single-poster > img").attr("src")
        val plot = document.select(".description").text()
        val episodesElements = document.select("#episode-list a[href]")

        if (episodesElements.isEmpty()) {
            return MovieLoadResponse(title, url, name, TvType.Movie, data, posterUrl, null, plot)
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
            url,
            name,
            TvType.TvSeries,
            episodes,
            posterUrl,
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
        val document = if (data.startsWith("http"))
            app.get(data).document.select("#link-list").first()
        else Jsoup.parse(data)

        document?.select(".link-to-video")?.forEach { item ->
            val decoded = base64Decode(item.select("a").attr("data-iframe"))
            val link = tryParseJson<LinkElement>(decoded)?.src ?: return@forEach
            loadExtractor(link, subtitleCallback, callback)
        }
        return true
    }
}

data class LinkElement(
    @JsonProperty("src") val src: String
)

data class MovieSearchResponse(
    val name: String,
    val url: String,
    val provider: String,
    val type: TvType,
    val poster: String,
    val year: Int?,
    val description: String? // Dodaj opis
)
