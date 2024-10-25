package com.lagradost

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
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

    private suspend fun fetchDocument(url: String): Document? {
        return try {
            val response = app.get(url, headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"))
            response.document
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = fetchDocument(mainUrl) ?: return HomePageResponse(emptyList())
        val sliderItems = document.select("#posters a")
        val categories = ArrayList<HomePageList>()

        val title = "Nowości"
        val items = sliderItems.mapNotNull { item ->
            val name = item.attr("original-title")
            val href = item.attr("href")
            val poster = item.select("img").attr("src")

            MovieSearchResponse(
                name,
                href,
                this.name,
                TvType.Movie,
                poster,
                null
            )
        }

        categories.add(HomePageList(title, items))
        return HomePageResponse(categories)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search/qf?q=$query"
        val document = fetchDocument(url) ?: return emptyList()
        val searchResults = document.select("#movie-result a")

        return searchResults.mapNotNull { result ->
            val href = result.attr("href")
            val img = result.select("img").attr("src")
            val name = result.attr("title")

            MovieSearchResponse(
                name,
                href,
                this.name,
                TvType.Movie,
                img,
                null
            )
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = fetchDocument(url) ?: return MovieLoadResponse("Error", url, name, TvType.Movie, "", "", null, "Unable to load")
        val documentTitle = document.select("title").text().trim()

        if (documentTitle.startsWith("Logowanie")) {
            throw RuntimeException("Strona wymaga zalogowania się, nie można kontynuować.")
        }

        var title = document.select(".scope_right .title").text()
        val plot = document.select(".movieDesc").text()
        val posterUrl = document.select(".scope_left img").attr("src")
        val episodesElements = document.select("#episode-list a[href]")

        if (episodesElements.isEmpty()) {
            return MovieLoadResponse(title, url, name, TvType.Movie, null, posterUrl, null, plot)
        }

        title = document.selectFirst(".scope_right .title")?.text() ?: "Nieznany tytuł"
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
            fetchDocument(data)?.select("#link-list")?.first()
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
