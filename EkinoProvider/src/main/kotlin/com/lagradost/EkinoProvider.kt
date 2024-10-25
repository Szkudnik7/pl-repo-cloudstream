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
            // Log the exception for debugging
            e.printStackTrace()
            null
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = fetchDocument(mainUrl) ?: return HomePageResponse(emptyList())
        val lists = document.select(".swiper-slide") // Adjust if needed
        val categories = ArrayList<HomePageList>()

        val title = "Nowości"
        val items = lists.mapNotNull { item ->
            val a = item.select("a").first() ?: return@mapNotNull null
            val name = a.attr("movieDesc") // Title
            val href = a.attr("<a href="https://furher.in/e/pi5wikwh16yp" target="_blank" class="buttonprch"></a>") // Link
            val poster = item.select("img[src]").attr("src") // Poster
            val year = item.select(".m-more").text().split("|").firstOrNull()?.trim()?.toIntOrNull() // Year

            MovieSearchResponse(
                name,
                href,
                this.name,
                TvType.Movie,
                poster,
                year
            )
        }

        categories.add(HomePageList(title, items))
        return HomePageResponse(categories)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/wyszukiwarka?phrase=$query"
        val document = fetchDocument(url) ?: return emptyList()
        val lists = document.select("#advanced-search > div")
        val movies = lists[1].select("div:not(.clearfix)")
        val series = lists[3].select("div:not(.clearfix)")

        if (movies.isEmpty() && series.isEmpty()) return emptyList()

        fun getVideos(type: TvType, items: Elements): List<SearchResponse> {
            return items.mapNotNull { i ->
                val href = i.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                val img = i.selectFirst("a > img[src]")?.attr("src")?.replace("/thumb/", "/big/")
                val name = i.selectFirst(".title")?.text() ?: return@mapNotNull null

                if (type === TvType.TvSeries) {
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
                    MovieSearchResponse(name, href, this.name, type, img, null)
                }
            }
        }

        return getVideos(TvType.Movie, movies) + getVideos(TvType.TvSeries, series)
    }

    override suspend fun load(url: String): LoadResponse {
        val document = fetchDocument(url) ?: return MovieLoadResponse("Error", url, name, TvType.Movie, "", "", null, "Unable to load")
        val documentTitle = document.select("title").text().trim()

        if (documentTitle.startsWith("Logowanie")) {
            throw RuntimeException("This page seems to be locked behind a login-wall on the website, unable to scrape it. If it is not please report it.")
        }

        var title = document.select("span[itemprop=name]").text()
        val data = document.select("#link-list").outerHtml()
        val posterUrl = document.select("#single-poster > img").attr("src")
        val plot = document.select(".description").text()
        val episodesElements = document.select("#episode-list a[href]")

        if (episodesElements.isEmpty()) {
            return MovieLoadResponse(title, url, name, TvType.Movie, data, posterUrl, null, plot)
        }

        title = document.selectFirst(".info")?.parent()?.select("h2")?.text()!!
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
