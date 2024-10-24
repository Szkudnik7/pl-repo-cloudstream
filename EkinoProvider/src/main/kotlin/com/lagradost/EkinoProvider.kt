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
        val url = "$mainUrl/wyszukiwarka?phrase=$query"
        val document = fetchDocument(url) ?: return emptyList()
        val lists = document.select(".mostPopular .list li")

        return lists.mapNotNull { item ->
            val href = item.select("a").attr("href")
            val img = "https:" + item.select("img[src]").attr("src")
            val name = item.select(".title a").text()

            MovieSearchResponse(name, href, this.name, TvType.Movie, img, null)
        }
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

        document?.select(".link-to-video a")?.forEach { item ->
            val videoUrl = item.attr("href")
            loadExtractor(videoUrl, subtitleCallback, callback)
        }
        return true
    }
}

data class LinkElement(
    @JsonProperty("src") val src: String
)
