package com.tsygankov.weatherforecast.demo

import android.app.Application
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import com.tsygankov.weatherforecast.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class AndroidComponentsActivityTest {
    @Test
    fun `activity shows a fragment with bound topics after recreation`() {
        ActivityScenario.launch(AndroidComponentsActivity::class.java).use { scenario ->
            scenario.onActivity(::assertTopicsDisplayed)
            scenario.recreate()
            scenario.onActivity(::assertTopicsDisplayed)
        }
    }

    private fun assertTopicsDisplayed(activity: AndroidComponentsActivity) {
        activity.supportFragmentManager.executePendingTransactions()
        val fragment = activity.supportFragmentManager.fragments.single()
        assertTrue(fragment is AndroidComponentsFragment)
        val list = fragment.requireView() as RecyclerView
        assertEquals(R.id.android_components_demo_list, list.id)
        val adapter = requireNotNull(list.adapter)
        val topics = activity.resources.getStringArray(R.array.android_components_demo_topics)
        assertEquals(topics.size, adapter.itemCount)
        assertTrue(topics.isNotEmpty())

        val position = topics.lastIndex
        assertEquals(topics[position], boundTopic(adapter, list, position))
    }

    private fun <Holder : RecyclerView.ViewHolder> boundTopic(
        adapter: RecyclerView.Adapter<Holder>,
        list: RecyclerView,
        position: Int,
    ): String {
        val holder = adapter.createViewHolder(list, adapter.getItemViewType(position))
        adapter.bindViewHolder(holder, position)
        return (holder.itemView as TextView).text.toString()
    }
}
