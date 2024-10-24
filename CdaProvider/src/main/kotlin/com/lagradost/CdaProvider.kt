package com.lagradost

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.network.CloudflareKiller
import org.jsoup.Jsoup
import org.jsoup.select.Elements

class CdaProvider : MainAPI() {
    override var mainUrl = "https://cda-hd.cc/"
    override var name = "cda-hd.cc"
    override var lang = "pl"
    override val hasMainPage = true
    override val usesWebView = true
    override val supportedTypes = setOf(
        TvType.TvSeries,
        TvType.Movie
    )

    private val interceptor = CloudflareKiller()

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = fetchDocument(mainUrl) ?: return HomePageResponse(emptyList())
        val lists = document.select(".item")
        val categories = ArrayList<HomePageList>()

        val title = document.select(".box_item h1")
        val items = lists.mapNotNull { item ->
            val a = item.select("a").first() ?: return@mapNotNull null
            val name = item.select(".title a").text()
            val href = mainUrl + a.attr(".buttonprch:visited")
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
        val url = "$mainUrl/wyszukaj?phrase=$query"
        val document = app.get(url, interceptor = interceptor).document
        val lists = document.select("#advanced-search > div")
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
                        properUrl(href)!!,
                        this.name,
                        type,
                        properUrl(img)!!,
                        null,
                        posterHeaders = interceptor.getCookieHeaders(url).toMap()
                    )
                } else {
                    MovieSearchResponse(name, properUrl(href)!!, this.name, type, properUrl(img)!!, null, posterHeaders = interceptor.getCookieHeaders(url).toMap())
                }
            }
        }
        return getVideos(TvType.Movie, movies) + getVideos(TvType.TvSeries, series)
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
        val plot = document.select(".description").text()
        val episodesElements = document.select("#episode-list a[href]")
        if (episodesElements.isEmpty()) {
            return MovieLoadResponse(title, properUrl(url)!!, name, TvType.Movie, data, properUrl(posterUrl)!!, null, plot)
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
        else if (data.startsWith("URL"))
            app.get(properUrl(data)!!).document.select("#link-list").first()
        else Jsoup.parse(data)

        document?.select(".link-to-video")?.apmap { item ->
            val decoded = base64Decode(item.select("a").attr("data-iframe"))
            val link = tryParseJson<LinkElement>(decoded)?.src ?: return@apmap
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
