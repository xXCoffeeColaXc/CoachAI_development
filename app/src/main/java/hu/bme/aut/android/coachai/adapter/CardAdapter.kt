package hu.bme.aut.android.coachai.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import hu.bme.aut.android.coachai.data.CardItem
import hu.bme.aut.android.coachai.databinding.ShotsItemBinding

class CardAdapter(private val cardList: List<CardItem>) : RecyclerView.Adapter<CardAdapter.CardViewHolder>() {

    inner class CardViewHolder(private val shotsItemBinding: ShotsItemBinding) : RecyclerView.ViewHolder(shotsItemBinding.root) {
        fun bind(cardItem: CardItem) {
            shotsItemBinding.cardItemId.text = cardItem.swingID.toString()
            shotsItemBinding.cardItemScore.text = cardItem.swingScore.toString()
            shotsItemBinding.cardItemRecommendation.text = cardItem.swingRecommendation
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val binding = ShotsItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CardViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        holder.bind(cardList[position])
    }

    override fun getItemCount() = cardList.size
}
