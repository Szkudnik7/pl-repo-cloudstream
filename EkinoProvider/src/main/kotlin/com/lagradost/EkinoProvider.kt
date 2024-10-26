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
import java.util.concurrent. synchronized

class EkinoProvider : MainAPI() {

    private val MAIN_URL = "https://ekino-tv.pl/"
    private val NAME = "ekino-tv.pl"
    private val LANG = "pl"
    private val interceptor = CloudflareKiller()

    override fun getMainUrl(): String {
        return MAIN_URL
    }

    override fun getName(): String {
        return NAME
    }

    override fun getLang(): String {
        return LANG
    }

    override fun hasMainPage(): Boolean {
        return true
    }

    override fun usesWebView(): Boolean {
        return true
    }

    override fun getSupportedTypes(): Set<TvType> {
        return setOf(TvType.TvSeries, TvType.Movie)
    }

    @Synchronized // Avoid concurrency issues if necessary
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = AppUtils.getSafeDocument(getClient(), MAIN_URL, interceptor)
        val listElements = document.select("mainWrap") // Adjust selector if needed

        val categories = ArrayList<HomePageList>()
        for (listElement in listElements) {
            val title = capitalizeString(listElement.select("h3").text().toLowerCase().trim())
            val items = listElement.select(".nowa-poster").map { item ->
                val poster = item.attr("src")
                MovieSearchResponse(title, poster, item.attr("name"), TvType.Movie, null, 0)
            }
            categories.add(HomePageList(title, items))
        }
        return HomePageResponse(categories)
    }

    @Synchronized // Avoid concurrency issues if necessary
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$MAIN_URL/wyszukaj?phrase=$query"
        val document = AppUtils.getSafeDocument(getClient(), url, interceptor)
        val lists = document.select("#advanced-search > div")

        // Handle potential index changes or missing elements gracefully
        val movies = lists.size > 1 ? lists[1].select("div:not(.clearfix)") : Elements.empty()
        val series = lists.size > 3 ? lists[3].select("div:not(.clearfix)") : Elements.empty()

        val results = ArrayList<SearchResponse>()
        results.addAll(getVideos(TvType.Movie, movies))
        results.addAll(getVideos(TvType.TvSeries, series))
        return results
    }

    private fun getVideos(type: TvType, items: Elements): List<SearchResponse> {
        return items.stream()
            .mapNotNull { item ->
                val href = item.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                val img = item.selectFirst("a > img[src]")?.attr("src")?.replace("/thumb/", "/big/")
                val name = item.selectFirst(".title")?.text() ?: return@mapNotNull null

                if (type == TvType.TvSeries) {
                    TvSeriesSearchResponse(
                        name,
                        properUrl(href),
                        NAME,
                        type,
                        properUrl(img),
                        null,
                        interceptor.getCookieHeaders(url).toMap()
                    )
                } else {
                    MovieSearchResponse(
                        name,
                        properUrl(href),
                        NAME,
                        type,
                        properUrl(img),
                        null,
                        interceptor.getCookieHeaders(url).toMap()
                    )
                }
            }
            .collect(Collectors.toList()) // Convert stream to list
    }

    private fun properUrl(inUrl: String?): String? {
        if (inUrl == null) return null
        return fixUrl(inUrl.replace("^URL".
