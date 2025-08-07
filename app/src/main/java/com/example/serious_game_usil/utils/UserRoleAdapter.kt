package com.example.serious_game_usil.utils

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ListAdapter
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.serious_game_usil.databinding.ItemUserRoleBinding
import com.example.serious_game_usil.R
import com.example.serious_game_usil.data.UserListItem




class UserRoleAdapter(
    private val listener: OnUserClickListener
) : RecyclerView.Adapter<UserRoleAdapter.UserViewHolder>() {

    private var users = listOf<UserListItem>()

    interface OnUserClickListener {
        fun onUserClick(user: UserListItem)
    }

    fun submitList(newUsers: List<UserListItem>) {
        users = newUsers
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder =
        UserViewHolder(
            ItemUserRoleBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(users[position])
    }

    override fun getItemCount(): Int = users.size

    inner class UserViewHolder(
        private val binding: ItemUserRoleBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    listener.onUserClick(users[position])
                }
            }
        }

        fun bind(user: UserListItem) = with(binding) {
            userNameText.text = user.nombresApellidos
            userUsernameText.text = "@${user.nombreUsuario}"
            userEmailText.text = user.correo
            userAvatarText.text = user.nombresApellidos
                .split(" ")
                .take(2)
                .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                .joinToString("")
                .ifEmpty { "?" }

            statusChip.apply {
                if (user.activo) {
                    text = "Activo"
                    setChipBackgroundColorResource(R.color.success_color)
                    setTextColor(ContextCompat.getColor(context, R.color.success_color))
                } else {
                    text = "Inactivo"
                    setChipBackgroundColorResource(R.color.error_color)
                    setTextColor(ContextCompat.getColor(context, R.color.error))
                }
            }
        }
    }
}