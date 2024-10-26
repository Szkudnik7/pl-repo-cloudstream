package com.lagradost

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.network.CloudflareKiller
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements

class EkinoProvider : MainAPI() {
    override var mainUrl = "https://ekino-tv.pl/"
    override var name = "Ekino"
    override var lang = "pl"
    override val hasMainPage = true
    override val usesWebView = true
    override val supportedTypes = setOf(
        TvType.TvSeries,
        TvType.Movie
    )

    private val interceptor = CloudflareKiller()

    // Funkcja do pobierania głównej strony z listą kategorii i elementów
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(mainUrl).document
        val categories = ArrayList<HomePageList>()

        // Selekcja sekcji zawierających filmy lub seriale
        val listElements = document.select(".mainWrap")
        for (listElement in listElements) {
            val title = listElement.select("h3").text().capitalize() // Pobiera tytuł kategorii i kapitalizuje

            // Pobiera listę elementów z plakatami i linkami do filmów/seriali
            val items = listElement.select(".nowa-poster").mapNotNull { i ->
                val poster = i.selectFirst("img")?.attr("src") ?: return@mapNotNull null
                val href = i.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                val itemName = i.selectFirst("a")?.attr("title") ?: return@mapNotNull null
                
                MovieSearchResponse(
                    title = itemName,
                    url = properUrl(href)!!,
                    apiName = this.name,
                    type = TvType.Movie,
                    posterUrl = properUrl(poster)!!,
                    posterHeaders = interceptor.getCookieHeaders(mainUrl).toMap()
                )
            }
            categories.add(HomePageList(title, items))
        }
        return HomePageResponse(categories)
    }

    // Funkcja do wyszukiwania filmów i seriali na stronie
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/wyszukaj?phrase=$query"
        val document = app.get(url, interceptor = interceptor).document
        val lists = document.select("#advanced-search > div")

        // Wybiera elementy dla filmów i seriali
        val movies = lists.getOrNull(1)?.select("div:not(.clearfix)") ?: Elements()
        val series = lists.getOrNull(3)?.select("div:not(.clearfix)") ?: Elements()

        fun getVideos(type: TvType, items: Elements): List<SearchResponse> {
            return items.mapNotNull { i ->
                val href = i.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                val img = i.selectFirst("a > img")?.attr("src")?.replace("/thumb/", "/big/") ?: return@mapNotNull null
                val name = i.selectFirst(".title")?.text() ?: return@mapNotNull null

                if (type == TvType.TvSeries) {
                    TvSeriesSearchResponse(
                        name = name,
                        url = properUrl(href)!!,
                        apiName = this.name,
                        type = type,
                        posterUrl = properUrl(img)!!,
                        posterHeaders = interceptor.getCookieHeaders(url).toMap()
                    )
                } else {
                    MovieSearchResponse(
                        name = name,
                        url = properUrl(href)!!,
                        apiName = this.name,
                        type = type,
                        posterUrl = properUrl(img)!!,
                        posterHeaders = interceptor.getCookieHeaders(url).toMap()
                    )
                }
            }
        }

        return getVideos(TvType.Movie, movies) + getVideos(TvType.TvSeries, series)
    }

    // Funkcja ładowania szczegółowych danych filmu/serialu
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, interceptor = interceptor).document
        if (document.title().startsWith("Logowanie")) {
            throw RuntimeException("This page requires login. Unable to scrape.")
        }

        val title = document.select("span[itemprop=name]").text().ifEmpty { document.select("h1").text() }
        val data = document.select("#link-list").outerHtml()
        val posterUrl = document.select("#single-poster img").attr("src")
        val plot = document.select(".description").text()
        val episodesElements = document.select("#episode-list a[href]")

        return if (episodesElements.isEmpty()) {
            MovieLoadResponse(
                title = title,
                url = properUrl(url)!!,
                apiName = name,
                type = TvType.Movie,
                data = data,
                posterUrl = properUrl(posterUrl)!!,
                plot = plot
            )
        } else {
            val episodes = episodesElements.mapNotNull { episode ->
                val episodeTitle = episode.text()
                val regex = Regex("""\[s(\d{1,3})e(\d{1,3})]""").find(episodeTitle) ?: return@mapNotNull null
                Episode(
                    url = properUrl(episode.attr("href"))!!,
                    name = episodeTitle.split("]").last().trim(),
                    season = regex.groupValues[1].toIntOrNull(),
                    episode = regex.groupValues[2].toIntOrNull()
                )
            }

            TvSeriesLoadResponse(
                title = title,
                url = properUrl(url)!!,
                apiName = name,
                type = TvType.TvSeries,
                episodes = episodes,
                posterUrl = properUrl(posterUrl)!!,
                plot = plot
            )
        }
    }

    // Funkcja pobierania linków wideo
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = when {
            data.startsWith("http") -> app.get(data).document
            data.startsWith("URL") -> app.get(properUrl(data)!!).document
            else -> Jsoup.parse(data)
        }
        document.select(".link-to-video a[data-iframe]").forEach { item ->
            val decoded = base64Decode(item.attr("data-iframe"))
            val link = tryParseJson<LinkElement>(decoded)?.src ?: return@forEach
            loadExtractor(link, subtitleCallback, callback)
        }
        return true
    }

    // Pomocnicza funkcja do przetwarzania URL-ów
    private fun properUrl(inUrl: String?): String? {
        return inUrl?.let { fixUrl(it.replace("^URL".toRegex(), "/")) }
    }
}

data class LinkElement(
    @JsonProperty("src") val src: String
)
