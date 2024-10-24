package com.lagradost

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

open class EkinoProvider : MainAPI() {
    override var mainUrl = "https://ekino-tv.pl/"
    override var name = "ekino-tv.pl"
    override var lang = "pl"
    override val hasMainPage = true
    override val usesWebView = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

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
        val lists = document.select(".mostPopular .list li")
        val categories = ArrayList<HomePageList>()

        val title = "Gorące Filmy"
        val items = lists.mapNotNull { item ->
            val a = item.select("a").first() ?: return@mapNotNull null
            val name = item.select(".title a").text()
            val href = mainUrl + a.attr("href")
            val poster = "https:" + item.select("img[src]").attr("src")
            val year = item.select(".cates").text().split("|").firstOrNull()?.trim()?.toIntOrNull()
            val description = item.select(".movieDesc").text()

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
        // Wykorzystaj adres do wyszukiwania z formularza
        val url = "$mainUrl/search/qf/?q=$query"
        val document = app.get(url).document
        val lists = document.select("body > div.mainWrap > div.col-md-12.movie-wrap > div:nth-child(1)")
        val movies = lists[1].select("div:not(.clearfix)")
        val series = lists[3].select("div:not(.clearfix)")
        if (movies.isEmpty() && series.isEmpty()) return ArrayList()
        fun getVideos(type: TvType, items: Elements): List<SearchResponse> {
            return items.mapNotNull { i ->
                val href = i.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                val img =
                    i.selectFirst("a > img[src]")?.attr("src")?.replace("/thumb/", "/big/")
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

        val title = document.select("h1.title").text()
        val posterUrl = "https:" + document.select("#single-poster img").attr("src")
        val plot = document.select(".descriptionMovie").text()
        val data = document.select("#link-list").outerHtml()

        return MovieLoadResponse(
            title,
            url,
            name,
            TvType.Movie,
            data,
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

        document?.select("a:-webkit-any-link")?.forEach { item ->
            val videoUrl = item.attr("href")
            loadExtractor(videoUrl, subtitleCallback, callback)
        }
        return true
    }
}

data class LinkElement(
    @JsonProperty("src") val src: String
)
