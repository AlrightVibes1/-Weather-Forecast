package com.tsygankov.weatherforecast.demo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tsygankov.weatherforecast.R

class AndroidComponentsActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = FragmentContainerView(this).apply {
            id = R.id.android_components_demo_container
        }
        ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
            val safeArea = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            view.setPadding(safeArea.left, safeArea.top, safeArea.right, safeArea.bottom)
            insets
        }
        setContentView(container)

        // При пересоздании Activity FragmentManager сам восстанавливает фрагмент.
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .add(R.id.android_components_demo_container, AndroidComponentsFragment())
                .commit()
        }
    }
}

// Учебный экран из debug source set показывает, как Fragment создаёт свой View
// и размещает обычный Android-список с адаптером.
class AndroidComponentsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = RecyclerView(inflater.context).apply {
        id = R.id.android_components_demo_list
        layoutManager = LinearLayoutManager(context)
        adapter = AndroidTopicsAdapter(resources.getStringArray(R.array.android_components_demo_topics))
    }
}

private class AndroidTopicsAdapter(
    private val topics: Array<String>,
) : RecyclerView.Adapter<AndroidTopicsAdapter.TopicHolder>() {
    class TopicHolder(val label: TextView) : RecyclerView.ViewHolder(label)

    override fun getItemCount(): Int = topics.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TopicHolder {
        val row = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false) as TextView
        return TopicHolder(row)
    }

    // При прокрутке RecyclerView отдаёт адаптеру существующую строку для привязки новой темы.
    override fun onBindViewHolder(holder: TopicHolder, position: Int) {
        holder.label.text = topics[position]
    }
}
