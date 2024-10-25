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
    override var name = "Ekino"
    override var lang = "pl"
    override val hasMainPage = true
    override val usesWebView = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(mainUrl).document
        val categories = ArrayList<HomePageList>()

        // Zbieramy wszystkie kategorie
        document.select(".item-list").forEach { listElement ->
            val title = listElement.parent().select("h3").text().trim()
            val items = listElement.select(".poster").mapNotNull { posterElement ->
                val linkElement = posterElement.parent()
                val href = linkElement.attr("href")
                val name = linkElement.attr("title")
                val poster = posterElement.select("img[src]").attr("src")
                val year = linkElement.select(".year").text().toIntOrNull()

                // Tworzymy obiekt MovieSearchResponse
                MovieSearchResponse(name, href, name, TvType.Movie, poster, year)
            }
            categories.add(HomePageList(title, items))
        }

        return HomePageResponse(categories)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search/qf"
        val document = app.post(url, mapOf("q" to query)).document
        val movies = document.select("#mres > div") // Używamy selektora do wyników wyszukiwania

        return movies.mapNotNull { movieElement ->
            val href = movieElement.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val img = movieElement.selectFirst("a > img[src]")?.attr("src")
            val name = movieElement.selectFirst(".title")?.text() ?: return@mapNotNull null

            MovieSearchResponse(name, href, name, TvType.Movie, img, null)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.select("span[itemprop=name]").text()
        val posterUrl = document.select("#single-poster > img").attr("src")
        val plot = document.select(".description").text()

        return MovieLoadResponse(title, url, name, TvType.Movie, "", posterUrl, null, plot)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = if (data.startsWith("http")) app.get(data).document else Jsoup.parse(data)
        document.select(".link-to-video").forEach { item ->
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
