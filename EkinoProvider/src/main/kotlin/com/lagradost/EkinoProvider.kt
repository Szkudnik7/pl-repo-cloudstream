package your.package.name

import com.example.app.MainPageRequest
import com.example.app.HomePageList
import com.example.app.MovieSearchResponse
import com.example.app.TvType
import com.example.app.HomePageResponse
import com.example.app.BaseProvider
import org.jsoup.Jsoup

class YourProvider : BaseProvider() {

    private val mainUrl = "https://ekino-tv.pl"

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = Jsoup.connect(mainUrl).get()
        val categories = ArrayList<HomePageList>()

        // Sekcja "Gorące Filmy"
        val popularMoviesSection = document.select(".mostPopular .list")
        if (popularMoviesSection.isNotEmpty()) {
            val popularMovies = popularMoviesSection.select("li").mapNotNull { movie ->
                val link = movie.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                val title = movie.selectFirst(".title a")?.text() ?: return@mapNotNull null
                val poster = movie.selectFirst("img[src]")?.attr("src") ?: ""
                val year = movie.selectFirst(".cates")?.text()?.substringBefore("|")?.trim()?.toIntOrNull()
                val description = movie.selectFirst(".movieDesc")?.text() ?: ""

                MovieSearchResponse(
                    title,
                    "$mainUrl$link",
                    this.name,
                    TvType.Movie,
                    poster,
                    year,
                    description
                )
            }
            categories.add(HomePageList("Gorące Filmy", popularMovies))
        }

        // Sekcja "Najnowsze Filmy"
        val latestMoviesSection = document.select(".mostPopular .top .list")
        if (latestMoviesSection.isNotEmpty()) {
            val latestMovies = latestMoviesSection.select("li").mapNotNull { movie ->
                val link = movie.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                val title = movie.selectFirst(".title a")?.text() ?: return@mapNotNull null
                val poster = movie.selectFirst("img[src]")?.attr("src") ?: ""
                val year = movie.selectFirst(".cates")?.text()?.substringBefore("|")?.trim()?.toIntOrNull()
                val description = movie.selectFirst(".movieDesc")?.text() ?: ""

                MovieSearchResponse(
                    title,
                    "$mainUrl$link",
                    this.name,
                    TvType.Movie,
                    poster,
                    year,
                    description
                )
            }
            categories.add(HomePageList("Najnowsze Filmy", latestMovies))
        }

        return HomePageResponse(categories)
    }
}
