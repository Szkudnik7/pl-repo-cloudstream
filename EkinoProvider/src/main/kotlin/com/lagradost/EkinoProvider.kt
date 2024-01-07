package com.lagradost

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import org.jsoup.select.Elements

class EkinoProvider : MainAPI() {
    override var mainUrl = "https://ekino-tv.pl"
    override var name = "ekino-tv.pl"
    override var lang = "pl"
    override val hasMainPage = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val document = app.get(mainUrl).document
        val lists = document.select("#item-list,#series-list")
        val categories = ArrayList<HomePageList>()
        for (l in lists) {
            val title = capitalizeString(l.parent()!!.select("h3").text().lowercase())
            val items = l.select(".poster").map { i ->
                val name = i.select("a[href]").attr("title")
                val href = i.select("a[href]").attr("href")
                val poster = i.select("img[src]").attr("src")
                val year = l.select(".film_year").text().toIntOrNull()
                if (l.hasClass("series-list")) TvSeriesSearchResponse(
                    name,
                    href,
                    this.name,
                    TvType.TvSeries,
                    poster,
                    year,
                    null
                ) else MovieSearchResponse(
                    name,
                    href,
                    this.name,
                    TvType.Movie,
                    poster,
                    year
                )
            }
            categories.add(HomePageList(title, items))
        }
        return HomePageResponse(categories)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search/qf/?q=$query"
        val document = app.get(url).document
        val lists = document.select(".mainWrap > .col-md-12 movie-wrap > div")
        val movies = lists[0].select("div:not(h3)")
        val series = lists[1].select("div:not(h3)")
        if (movies.isEmpty() && series.isEmpty()) return ArrayList()
        fun getVideos(type: TvType, items: Elements): List<SearchResponse> {
            return items.mapNotNull { i ->
                val href = i.selectFirst(".cover-list > a")?.attr("href") ?: return@mapNotNull null
                val img =
                    i.selectFirst(".cover-list > a > img")?.attr("src")?.replace("/thumb/", "/big/")
                val name = i.selectFirst(".opis-list > .title > a")?.text() ?: return@mapNotNull null
                val year = i.selectFirst(".info-categories > .cates > #text")?.text()?.toIntOrNull()
                if (type === TvType.TvSeries) {
                    TvSeriesSearchResponse(
                        name,
                        href,
                        this.name,
                        type,
                        img,
                        year,
                        null
                    )
                } else {
                    MovieSearchResponse(name, href, this.name, type, img, year)
                }
            }
        }
        return getVideos(TvType.Movie, movies) + getVideos(TvType.TvSeries, series)
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val documentTitle = document.select("title").text().trim()

        if (documentTitle.startsWith("Logowanie")) {
            throw RuntimeException("This page seems to be locked behind a login-wall on the website, unable to scrape it. If it is not please report it.")
        }

        var title = document.select("span[itemprop=title]").text()
        val data = document.select("#links").outerHtml()
        val posterUrl = document.select("#single-poster > img").attr("src")
        val year = document.select(".info > ul > li").getOrNull(1)?.text()?.toIntOrNull()
        val plot = document.select(".description").text()
        val episodesElements = document.select("#episode-list a[href]")
        if (episodesElements.isEmpty()) {
            return MovieLoadResponse(title, url, name, TvType.Movie, data, posterUrl, year, plot)
        }
        title = document.selectFirst(".info")?.parent()?.select("h2")?.text() ?: ""
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
            year,
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
            app.get(data).document.select("#links").first()
        else Jsoup.parse(data)

        document?.select(".link-to-video")?.apmap { item ->
            val decoded = base64Decode(item.select("a").attr("data-iframe"))
            val videoType = item.parent()?.select("td:nth-child(2)")?.text()
            val link = tryParseJson<LinkElement>(decoded)?.src ?: return@apmap
            loadExtractor(link, subtitleCallback) { extractedLink ->
                run {
                    callback(ExtractorLink(extractedLink.source, extractedLink.name + " " + videoType, extractedLink.url, extractedLink.referer, extractedLink.quality, extractedLink.isM3u8, extractedLink.headers, extractedLink.extractorData))
                }
            }
        }
        return true
    }
}

data class LinkElement(
    @JsonProperty("src") val src: String
)