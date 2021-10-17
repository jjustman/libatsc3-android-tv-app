package com.nextgenbroadcast.mobile.tvandroid.servicesGuide

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.recyclerview.widget.ScheduleLayoutManager
import com.nextgenbroadcast.mobile.tvandroid.R
import com.nextgenbroadcast.mobile.tvandroid.servicesGuide.items.GuideTimeItem
import com.nextgenbroadcast.mobile.tvandroid.servicesGuide.items.GuideDataItem
import com.nextgenbroadcast.mobile.tvandroid.servicesGuide.items.IGuideItem
import com.nextgenbroadcast.mobile.tvandroid.servicesGuide.items.GuideServiceItem
import com.nextgenbroadcast.mobile.tvandroid.servicesGuide.view.*

class ServiceGuideAdapter(
    private val layoutInflater: LayoutInflater,
    private val columnWidth: Int
) : RecyclerView.Adapter<ServiceGuideAdapter.ChannelItem<out IGuideItem>>() {

    private val items = mutableListOf<IGuideItem>()

    var selectedPosition: Int = NO_POSITION
        private set
    var selectedSpanIndex: Int = NO_POSITION
        private set

    private var recyclerView: RecyclerView? = null
    private var rowCount: Int = 0

    fun setItems(itemList: List<IGuideItem>) {
        //val diffUtilCallback = ServiceGuideDiffUtilCallback(items, itemList)
        //val diffResult = DiffUtil.calculateDiff(diffUtilCallback, false)

        notifyItemRangeRemoved(0, items.size)

        items.clear()
        items.addAll(itemList)

        if (selectedPosition >= itemList.size) selectedPosition = NO_POSITION
        if (itemList.isEmpty()) selectedSpanIndex = NO_POSITION

        notifyItemRangeInserted(0, itemList.size)

        //diffResult.dispatchUpdatesTo(this)
    }

    fun reset() {
        val sizeBefore = items.size
        items.clear()
        selectedPosition = NO_POSITION
        selectedSpanIndex = NO_POSITION

        notifyItemRangeRemoved(0, sizeBefore)
    }

    fun setSelected(position: Int, spanIndex: Int) {
        if (position != IGNORE_POSITION) selectedPosition = position
        if (spanIndex != IGNORE_POSITION) selectedSpanIndex = spanIndex

        /*recyclerView?.let { recycler ->
            val lm = recycler.layoutManager as? StaggeredGridLayoutManager ?: return
            lm.findFirstVisibleItemPositions(null).minOrNull()?.let { firstPosition ->
                lm.findLastVisibleItemPositions(null).maxOrNull()?.let { lastPosition ->
                    notifyItemRangeChanged(firstPosition, lastPosition - firstPosition + 1, Any())
                }
            }
        } ?:*/ notifyItemRangeChanged(0, items.size, Any())
    }

    fun getItem(position: Int) = items.getOrNull(position)

    fun findPosition(predicate: (IGuideItem) -> Boolean) = items.indexOfFirst(predicate)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelItem<out IGuideItem> {
        return when (viewType) {
            IGuideItem.TYPE_TIME -> TimeItemViewHolder(
                layoutInflater.inflate(R.layout.service_guide_time_item,
                    parent,
                    false) as ServiceGuideTimeItemView
            )

            IGuideItem.TYPE_SERVICE -> ServiceItemViewHolder(
                layoutInflater.inflate(R.layout.service_guide_service_item,
                    parent,
                    false) as ServiceGuideServiceItemView
            )

            IGuideItem.TYPE_DATA -> DataItemViewHolder(
                layoutInflater.inflate(R.layout.service_guide_data_item,
                    parent,
                    false) as ServiceGuideDataItemView
            )

            else -> throw IllegalArgumentException("Unsupported type: $viewType")
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ChannelItem<out IGuideItem>, position: Int) {
        when (getItemViewType(position)) {
            IGuideItem.TYPE_TIME -> (holder as TimeItemViewHolder).onBind(
                items[position] as GuideTimeItem
            )
            IGuideItem.TYPE_SERVICE -> (holder as ServiceItemViewHolder).onBind(
                items[position] as GuideServiceItem
            )
            IGuideItem.TYPE_DATA -> (holder as DataItemViewHolder).onBind(
                items[position] as GuideDataItem
            )
        }
    }

    override fun getItemViewType(position: Int): Int = items[position].type

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView

        rowCount = (recyclerView.layoutManager as? StaggeredGridLayoutManager)?.spanCount ?: 0
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        this.recyclerView = null
    }

    @Suppress("SameParameterValue")
    private fun notifyDataSetChanged(sizeBefore: Int, sizeAfter: Int, startFrom: Int) {
        when {
            sizeBefore == sizeAfter -> {
                notifyItemRangeChanged(startFrom, sizeAfter)
            }
            sizeBefore < sizeAfter -> {
                notifyItemRangeChanged(startFrom, sizeBefore)
                notifyItemRangeInserted(startFrom + sizeBefore, sizeAfter - sizeBefore)
            }
            else -> {
                notifyItemRangeChanged(startFrom, sizeAfter)
                notifyItemRangeRemoved(startFrom + sizeAfter, sizeBefore - sizeAfter)
            }
        }
    }

    open inner class ChannelItem<T : IGuideItem>(itemView: View) : RecyclerView.ViewHolder(itemView) {
        open fun onBind(item: T) {
            val generalWidth = columnWidth * item.size
            itemView.layoutParams.width = if (!GUIDE_UI_HACK && adapterPosition < rowCount) (generalWidth * 1.5).toInt() else generalWidth
            itemView.requestLayout()
        }
    }

    inner class TimeItemViewHolder(
        private val view: ServiceGuideTimeItemView
    ) : ChannelItem<GuideTimeItem>(view) {
        override fun onBind(item: GuideTimeItem) {
            super.onBind(item)

            view.bind(item)
        }
    }

    open inner class SpannedChannelItem<T : IGuideItem>(itemView: View) : ChannelItem<T>(itemView) {
        val spanIndex: Int = getLayoutParams().spanIndex

        protected fun getLayoutParams(): ScheduleLayoutManager.LayoutParams {
            return itemView.layoutParams as ScheduleLayoutManager.LayoutParams
        }
    }

    inner class ServiceItemViewHolder(
        private val view: ServiceGuideServiceItemView
    ) : SpannedChannelItem<GuideServiceItem>(view) {
        override fun onBind(item: GuideServiceItem) {
            super.onBind(item)

            view.isSelected = spanIndex == selectedSpanIndex
            view.bind(item)
        }
    }

    inner class DataItemViewHolder(
        private val view: ServiceGuideDataItemView
    ) : SpannedChannelItem<GuideDataItem>(view) {
        override fun onBind(item: GuideDataItem) {
            getLayoutParams().requestSpanIndex(item.rowIndex)

            super.onBind(item)

            view.isActivated = adapterPosition == selectedPosition
            view.bind(item)
        }
    }

    inner class ServiceGuideDiffUtilCallback(
        private val oldList: List<IGuideItem>,
        private val newList: List<IGuideItem>
    ) : DiffUtil.Callback() {

        override fun getOldListSize() = oldList.size

        override fun getNewListSize() = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem: IGuideItem = oldList[oldItemPosition]
            val newItem: IGuideItem = newList[newItemPosition]

            /*return oldItem.type == newItem.type
                    && when (newItem.type) {
                IGuideItem.TYPE_DATA -> return (newItem as GuideDataItem).id == (oldItem as GuideDataItem).id
                else -> true
            }*/
            return oldItem.type == newItem.type
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }

    companion object {
        const val NO_POSITION = -2
        const val IGNORE_POSITION = -3
    }
}