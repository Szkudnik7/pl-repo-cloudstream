open class EkinoProvider : MainAPI() {
    // ... existing properties remain unchanged ...

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = fetchDocument(mainUrl) ?: return HomePageResponse(emptyList())
        val lists = document.select(".swiper-slide") // Adjust if needed
        val categories = ArrayList<HomePageList>()

        val title = "Nowości"
        val items = lists.mapNotNull { item ->
            val a = item.select("a").first() ?: return@mapNotNull null
            val name = a.attr("title") // Changed from "alt" to "title" for correct movie title
            val href = a.attr("href") // Link
            val poster = item.select("img[src]").attr("src") // Poster
            
            // Ensure the image URL is complete
            val fullPosterUrl = if (poster.startsWith("http")) poster else mainUrl + poster

            val year = item.select(".m-more").text().split("|").firstOrNull()?.trim()?.toIntOrNull() // Year

            MovieSearchResponse(
                name,
                href,
                this.name,
                TvType.Movie,
                fullPosterUrl, // Use the complete image URL
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
                val img = i.selectFirst("a > img[src]")?.attr("src")?.replace("/thumb/", "/big/") // Ensure high-res image
                
                // Ensure the image URL is complete
                val fullImgUrl = if (img.startsWith("http")) img else mainUrl + img
                val name = i.selectFirst(".title")?.text() ?: return@mapNotNull null

                if (type === TvType.TvSeries) {
                    TvSeriesSearchResponse(
                        name,
                        href,
                        this.name,
                        type,
                        fullImgUrl, // Use the complete image URL
                        null,
                        null
                    )
                } else {
                    MovieSearchResponse(name, href, this.name, type, fullImgUrl, null)
                }
            }
        }

        return getVideos(TvType.Movie, movies) + getVideos(TvType.TvSeries, series)
    }

    // ... remaining methods unchanged ...
}
