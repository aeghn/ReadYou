package me.ash.reader.ui.page.home.reading

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.ItemSnapshotList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.ash.reader.domain.model.article.ArticleFlowItem
import me.ash.reader.domain.model.article.ArticleWithFeed
import me.ash.reader.domain.service.RssService
import me.ash.reader.infrastructure.di.ApplicationScope
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.rss.RssHelper
import me.ash.reader.infrastructure.storage.AndroidImageDownloader
import javax.inject.Inject

@HiltViewModel
class ReadingViewModel @Inject constructor(
    private val rssService: RssService,
    private val rssHelper: RssHelper,
    @IODispatcher
    private val ioDispatcher: CoroutineDispatcher,
    @ApplicationScope
    private val applicationScope: CoroutineScope,
    private val imageDownloader: AndroidImageDownloader
) : ViewModel() {

    private val _readingState = MutableStateFlow(ReadingState())
    val readingState: StateFlow<ReadingState> = _readingState.asStateFlow()

    private var articleIdMap by mutableStateOf(mutableMapOf<Int, String>());

    private fun pushIdAndArticle(id: Int, articleId: String) {
        articleIdMap[id] = articleId
    }

    fun getArticleId(id: Int): String? {
        val sid = articleIdMap[id]
        return sid
    }

    fun getArticleIdOffset(offset: Int): String? {
        return getArticleId(_readingState.value.pagerCursor + offset)
    }

    private fun getArticlePageCursor(articleId: String): Int? {
        val sid = articleIdMap.entries.firstOrNull {
            it.value == articleId
        }?.key
        return sid
    }

    suspend fun getArticle(id: String?): ArticleWithFeed? {
        return if (id != null) {
            rssService.get().findArticleById(id)
        } else {
            null
        }
    }

    fun updateArticleIdAndCursor(
        articleId: String,
        cursor: Int,
    ) {
        updateReadingState(articleId, cursor)
    }

    fun initReadingState(
        articleId: String,
        total: Int,
        initCursor: Int
    ) {
        updateReadingState(articleId, total = total, initCursor = initCursor)
    }

    private fun updateReadingState(
        articleId: String,
        cursor: Int? = null,
        total: Int? = null,
        initCursor: Int? = null
    ) {
        if (initCursor != null && total != null) {
            _readingState.update { it.copy(pagerInitCursor = initCursor, pagerTotal = total) }
        }

        val cursor1 = getArticlePageCursor(articleId)

        if (cursor1 != null) {
            _readingState.update {
                it.copy(
                    articleId = articleId,
                    pagerCursor = cursor1
                )
            }
            pushIdAndArticle(cursor1, articleId)
        } else {
            _readingState.update {
                it.copy(
                    articleId = articleId,
                )
            }
        }



        setLoading()
        viewModelScope.launch(ioDispatcher) {
            rssService.get().findArticleById(articleId)?.run {
                _readingState.update {
                    it.copy(
                        articleId = this.article.id,
                        isFeedFullContent = this.feed.isFullContent,
                        isStarred = article.isStarred,
                        isUnread = article.isUnread,
                        rawDescription = article.rawDescription,
                        fullContent = article.fullContent,
                        link = article.link,
                        title = article.title
                    )
                }
            }
            readingState.value.let {
                if (it.isFeedFullContent) internalRenderFullContent()
                else renderDescriptionContent()
            }
        }
    }

    fun renderDescriptionContent() {
        _readingState.update {
            it.copy(
                content = ContentState.Description(
                    content = it.fullContent ?: it.rawDescription ?: ""
                )
            )
        }
    }

    fun renderFullContent() {
        viewModelScope.launch {
            internalRenderFullContent()
        }
    }

    private suspend fun internalRenderFullContent() {
        setLoading()
        runCatching {
            rssHelper.parseFullContent(
                readingState.value.link ?: "",
                readingState.value.title ?: ""
            )
        }.onSuccess { content ->
            _readingState.update { it.copy(content = ContentState.FullContent(content = content)) }
        }.onFailure { th ->
            Log.i("RLog", "renderFullContent: ${th.message}")
            _readingState.update { it.copy(content = ContentState.Error(th.message.toString())) }
        }
    }

    fun updateReadStatus(isUnread: Boolean) {
        applicationScope.launch(ioDispatcher) {
            _readingState.update { it.copy(isUnread = isUnread) }
            rssService.get().markAsRead(
                groupId = null,
                feedId = null,
                articleId = readingState.value.articleId,
                before = null,
                isUnread = isUnread,
            )
        }

    }

    fun markAsRead() = updateReadStatus(isUnread = false)

    fun markAsUnread() = updateReadStatus(isUnread = true)

    fun updateStarredStatus(isStarred: Boolean) {
        applicationScope.launch(ioDispatcher) {
            _readingState.update { it.copy(isStarred = isStarred) }
            readingState.value.articleId?.let {
                rssService.get().markAsStarred(
                    articleId = it,
                    isStarred = isStarred,
                )
            }
        }
    }

    fun setLoading() {
        _readingState.update {
            it.copy(content = ContentState.Loading)
        }
    }

    fun updatePageCursor(cursor: Int) {
        _readingState.update {
            it.copy(pagerCursor = cursor)
        }
    }

    fun insertArticleRelations(
        pagingItems: ItemSnapshotList<ArticleFlowItem>,
        articleId: String? = null,
        cursor: Int? = null
    ) {
        val items = pagingItems.items
        val currentId = articleId ?: readingState.value.articleId
        val index = items.indexOfFirst { item ->
            item is ArticleFlowItem.Article && item.articleWithFeed.article.id == currentId
        }
        var previousId: String? = null
        var nextId: String? = null

        if (index != -1 || currentId == null) {
            val prevIterator = items.listIterator(index)
            while (prevIterator.hasPrevious()) {
                Log.d("Log", "index: $index, previous: ${prevIterator.previousIndex()}")
                val prev = prevIterator.previous()
                if (prev is ArticleFlowItem.Article) {
                    previousId = prev.articleWithFeed.article.id
                    break
                }
            }
            val nextIterator = items.listIterator(index + 1)
            while (nextIterator.hasNext()) {
                Log.d("Log", "index: $index, next: ${nextIterator.nextIndex()}")
                val next = nextIterator.next()
                if (next is ArticleFlowItem.Article && next.articleWithFeed.article.id != currentId) {
                    nextId = next.articleWithFeed.article.id
                    break
                }
            }
        }

        if (currentId != null && cursor != null) {
            if (!articleIdMap.containsKey(cursor)) {
                articleIdMap[cursor] = currentId;
            }

            if (nextId != null && !articleIdMap.containsKey(cursor + 1)) {
                articleIdMap[cursor + 1] = nextId;
            }

            if (previousId != null && !articleIdMap.containsKey(cursor - 1)) {
                articleIdMap[cursor - 1] = previousId;
            }
        }
    }

    fun downloadImage(
        url: String,
        onSuccess: (Uri) -> Unit = {},
        onFailure: (Throwable) -> Unit = {}
    ) {
        viewModelScope.launch {
            imageDownloader.downloadImage(url).onSuccess(onSuccess).onFailure(onFailure)
        }
    }

    fun setArticleIdOffset(offset: Int) {
        val cursor = readingState.value.pagerCursor
        val articleId = articleIdMap[cursor + offset] ?: return
        val target = cursor + offset

        if (target >= 0) {
            _readingState.update {
                it.copy(
                    articleId = articleId,
                    pagerCursor = target
                )
            }
        }

    }
}

data class ReadingState(
    val articleId: String? = null,
    val link: String? = null,
    val title: String? = null,

    val isUnread: Boolean = false,
    val isStarred: Boolean = false,

    val fullContent: String? = null,
    val rawDescription: String? = null,
    val content: ContentState = ContentState.Loading,

    val isFeedFullContent: Boolean = false,

    val pagerInitCursor: Int = 0,
    val pagerTotal: Int = 0,
    val pagerCursor: Int = 0,
)


sealed interface ContentState {
    val text: String?
        get() {
            return when (this) {
                is Description -> content
                is Error -> message
                is FullContent -> content
                Loading -> null
            }
        }

    data class FullContent(val content: String) : ContentState
    data class Description(val content: String) : ContentState
    data class Error(val message: String) : ContentState
    data object Loading : ContentState
}
