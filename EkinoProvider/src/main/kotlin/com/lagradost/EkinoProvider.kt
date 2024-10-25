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

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(mainUrl).document
        val lists = document.select(".mostPopular ul.list")
        val categories = ArrayList<HomePageList>()
        for (l in lists) {
            val title = capitalizeString(l.parent()!!.select("h3").text().lowercase().trim())
            val items = l.select(".poster").map { i ->
                val a = i.parent()!!
                val name = a.attr("title")
                val href = a.attr("href")
                val poster = i.select("img[src]").attr("src")
                val year = a.select(".year").text().toIntOrNull()
                val banner = i.select(".banner-selector").attr("src") // Dostosuj selektor do banera
                MovieSearchResponse(
                    name,
                    href,
                    this.name,
                    TvType.Movie,
                    poster,
                    year,
                    banner // Dodaj baner do odpowiedzi
                )
            }
            categories.add(HomePageList(title, items))
        }
        return HomePageResponse(categories)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        // Przygotuj dane do wysłania
        val params = mapOf("q" to query)
        val response = app.post("$mainUrl/search/qf", params)

        // Parsowanie dokumentu
        val document = response.document
        val lists = document.select("#movie-result > div")

        // Zakładając, że wyniki są w divach wewnątrz lists
        val movies = lists.select("div.movie-item") // Dostosuj selektor do struktury HTML

        if (movies.isEmpty()) return emptyList()

        // Funkcja do przetwarzania wyników
        fun getVideos(items: Elements): List<SearchResponse> {
            return items.mapNotNull { item ->
                val href = item.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                val img = item.selectFirst("img[src]")?.attr("src")
                val name = item.selectFirst(".title")?.text() ?: return@mapNotNull null
                val banner = item.selectFirst(".banner-selector")?.attr("src") // Dostosuj selektor do banera

                MovieSearchResponse(
                    name,
                    properUrl(href)!!,
                    this.name,
                    TvType.Movie,
                    properUrl(img),
                    null,
                    properUrl(banner) // Dodaj baner do odpowiedzi
                )
            }
        }

        return getVideos(movies)
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, interceptor = interceptor).document
        val documentTitle = document.select("title").text().trim()

        if (documentTitle.startsWith("Logowanie")) {
            throw RuntimeException("This page seems to be locked behind a login-wall on the website, unable to scrape it. If it is not please report it.")
        }

        var title = document.select("span[itemprop=name]").text()
        val data = document.select("#link-list").outerHtml()
        val posterUrl = document.select("#single-poster > img").attr("src")
        val bannerUrl = document.select(".banner-selector").attr("src") // Dostosuj selektor do banera
        val plot = document.select(".description").text()
        val episodesElements = document.select("#episode-list a[href]")
        if (episodesElements.isEmpty()) {
            return MovieLoadResponse(title, properUrl(url)!!, name, TvType.Movie, data, properUrl(posterUrl)!!, properUrl(bannerUrl)!!, plot)
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
            properUrl(url)!!,
            name,
            TvType.TvSeries,
            episodes,
            properUrl(posterUrl)!!,
            properUrl(bannerUrl)!!, // Dodaj baner do odpowiedzi
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
        else if (data.startsWith("URL"))
            app.get(properUrl(data)!!).document.select("#link-list").first()
        else Jsoup.parse(data)

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

data class MovieSearchResponse(
    val title: String,
    val url: String,
    val provider: String,
    val type: TvType,
    val posterUrl: String,
    val year: Int?,
    val bannerUrl: String? // Dodano pole na baner
)

data class MovieLoadResponse(
    val title: String,
    val url: String,
    val provider: String,
    val type: TvType,
    val data: String,
    val posterUrl: String,
    val bannerUrl: String, // Dodano pole na baner
    val plot: String
)

data class TvSeriesLoadResponse(
    val title: String,
    val url: String,
    val provider: String,
    val type: TvType,
    val episodes: List<Episode>,
    val posterUrl: String,
    val bannerUrl: String, // Dodano pole na baner
    val plot: String
)
