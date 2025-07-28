package com.example.serious_game_usil.ui.admin.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.serious_game_usil.R
import com.example.serious_game_usil.data.UserListItem
import com.example.serious_game_usil.databinding.ItemUsersBinding
import com.bumptech.glide.Glide

import java.text.SimpleDateFormat
import java.util.*

class UsersAdapter(
    private val listener: OnUserActionListener
) : ListAdapter<UserListItem, UsersAdapter.UserViewHolder>(UserDiffCallback()) {

    interface OnUserActionListener {
        fun onWhatsAppClick(user: UserListItem)
        fun onEditClick(user: UserListItem)
        fun onToggleStatusClick(user: UserListItem)
        fun onDeleteClick(user: UserListItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val binding = ItemUsersBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return UserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class UserViewHolder(
        private val binding: ItemUsersBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(user: UserListItem) {
            binding.apply {
                // Información básica
                userNameTextView.text = user.nombresApellidos
                userDocumentTextView.text = "${user.tipoDocumento}: ${user.numeroDocumento}"

                // Género
                userGenderTextView.text = when(user.sexo.uppercase()) {
                    "M" -> "Masculino"
                    "F" -> "Femenino"
                    else -> user.sexo
                }

                // Fecha de nacimiento
                userBirthDateTextView.text = formatDate(user.fechaNacimiento)

                // Estado
                if (user.activo) {
                    userStatusChip.text = "Activo"
                    userStatusChip.chipBackgroundColor = ContextCompat.getColorStateList(
                        binding.root.context,
                        R.color.status_active
                    )
                    userStatusChip.chipIcon = ContextCompat.getDrawable(
                        binding.root.context,
                        R.drawable.ic_check_circle
                    )

                    btnToggleStatus.setIconResource(R.drawable.ic_close)
                    btnToggleStatus.backgroundTintList = ContextCompat.getColorStateList(
                        binding.root.context,
                        R.color.red
                    )
                } else {
                    userStatusChip.text = "Inactivo"
                    userStatusChip.chipBackgroundColor = ContextCompat.getColorStateList(
                        binding.root.context,
                        R.color.status_inactive
                    )
                    userStatusChip.chipIcon = ContextCompat.getDrawable(
                        binding.root.context,
                        R.drawable.ic_close
                    )

                    btnToggleStatus.setIconResource(R.drawable.ic_check)
                    btnToggleStatus.backgroundTintList = ContextCompat.getColorStateList(
                        binding.root.context,
                        R.color.green
                    )
                }

                // Imagen de perfil
                if (!user.foto.isNullOrEmpty()) {
                    Glide.with(binding.root.context)
                        .load(user.foto)
                        .placeholder(R.drawable.ic_person_placeholder)
                        .error(R.drawable.ic_person_placeholder)
                        .circleCrop()
                        .into(userImageView)
                } else {
                    userImageView.setImageResource(R.drawable.ic_person_placeholder)
                }

                // Click listeners
                btnWhatsApp.setOnClickListener { listener.onWhatsAppClick(user) }
                btnEdit.setOnClickListener { listener.onEditClick(user) }
                btnToggleStatus.setOnClickListener { listener.onToggleStatusClick(user) }
                btnDelete.setOnClickListener { listener.onDeleteClick(user) }
            }
        }

        private fun formatDate(dateString: String): String {
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val outputFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val date = inputFormat.parse(dateString)
                outputFormat.format(date ?: Date())
            } catch (e: Exception) {
                dateString
            }
        }
    }

    class UserDiffCallback : DiffUtil.ItemCallback<UserListItem>() {
        override fun areItemsTheSame(oldItem: UserListItem, newItem: UserListItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: UserListItem, newItem: UserListItem): Boolean {
            return oldItem == newItem
        }
    }
}