package net.jkraemer.photos

import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PhotosViewModelTest {
    private val t = Instant.parse("2026-10-22T12:00:00Z")
    private val items = listOf(item(1, t), item(2, t.plusSeconds(1)), item(3, t.plusSeconds(2)))

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `timeline follows the repository`() {
        val repository = FakeMediaRepository(items)
        val viewModel = PhotosViewModel(repository)
        assertEquals(items, viewModel.timeline.value)
        repository.set(items.take(1))
        assertEquals(items.take(1), viewModel.timeline.value)
    }

    @Test fun `close pins to bottom when the newest item was last viewed`() {
        val viewModel = PhotosViewModel(FakeMediaRepository(items))
        viewModel.open(3)
        assertEquals(3L, viewModel.openItemId.value)
        viewModel.close(3)
        assertEquals(null, viewModel.openItemId.value)
        assertEquals(GridAnchor(3, pinnedToBottom = true), viewModel.gridAnchor.value)
        viewModel.close(2)
        assertEquals(GridAnchor(2, pinnedToBottom = false), viewModel.gridAnchor.value)
        viewModel.close(null)
        assertEquals(GridAnchor(null, pinnedToBottom = true), viewModel.gridAnchor.value)
    }

    @Test fun `launches at LARGE`() =
        assertEquals(net.jkraemer.photos.core.ZoomLevel.LARGE, PhotosViewModel(FakeMediaRepository(items)).zoomLevel.value)
}
