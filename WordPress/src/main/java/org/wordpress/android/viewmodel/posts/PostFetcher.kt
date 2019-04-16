package org.wordpress.android.viewmodel.posts

import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.LifecycleObserver
import android.arch.lifecycle.OnLifecycleEvent
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.generated.PostActionBuilder
import org.wordpress.android.fluxc.model.CauseOfOnPostChanged.UpdatePost
import org.wordpress.android.fluxc.model.LocalOrRemoteId.RemoteId
import org.wordpress.android.fluxc.model.PostModel
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.store.PostStore.OnPostChanged
import org.wordpress.android.fluxc.store.PostStore.RemotePostPayload

/**
 * Class which takes care of dispatching fetch post events while ignoring duplicate requests.
 */
class PostFetcher constructor(
    private val lifecycle: Lifecycle,
    private val dispatcher: Dispatcher
) : LifecycleObserver {
    private val ongoingRequests = HashSet<RemoteId>()

    init {
        dispatcher.register(this)
        lifecycle.addObserver(this)
    }

    /**
     * Handles the [Lifecycle.Event.ON_DESTROY] event to cleanup the registration for dispatcher and removing the
     * observer for lifecycle.
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    private fun onDestroy() {
        lifecycle.removeObserver(this)
        dispatcher.unregister(this)
    }

    // TODO: We should implement batch fetching when it's available in the API
    fun fetchPosts(site: SiteModel, remoteItemIds: List<RemoteId>) {
        remoteItemIds
                .filter {
                    val isDuplicate = ongoingRequests.contains(it)
                    !isDuplicate
                }
                .forEach { remoteId ->
                    ongoingRequests.add(remoteId)

                    val postToFetch = PostModel()
                    postToFetch.remotePostId = remoteId.value
                    dispatcher.dispatch(PostActionBuilder.newFetchPostAction(RemotePostPayload(postToFetch, site)))
                }
    }

    @Suppress("unused")
    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    fun onPostChanged(event: OnPostChanged) {
        (event.causeOfChange as? UpdatePost)?.let {
            ongoingRequests.remove(RemoteId((event.causeOfChange as UpdatePost).remotePostId))
        }
    }
}
