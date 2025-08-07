package com.example.serious_game_usil.ui.admin.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.serious_game_usil.R
import com.example.serious_game_usil.data.UserListItem
import com.example.serious_game_usil.databinding.ItemUsersBinding




class UsersAdapter(
    private val listener: OnUserActionListener
) : ListAdapter<UserListItem, UsersAdapter.UserViewHolder>(UserDiffCallback()) {

    interface OnUserActionListener {
        fun onEditClick(user: UserListItem)
        fun onDeleteClick(user: UserListItem)
        fun onToggleStatusClick(user: UserListItem)
        fun onChangePasswordClick(user: UserListItem)
        fun onWhatsAppClick(user: UserListItem)
        fun onViewProfileClick(user: UserListItem)
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
                // Información del usuario
                userNameTextView.text = user.nombresApellidos
                userDocumentTextView.text = "${user.tipoDocumento}: ${user.numeroDocumento}"
                userEmailTextView.text = user.correo

                // Estado del usuario
                if (user.activo) {
                    userStatusChip.text = "Activo"
                    userStatusChip.chipBackgroundColor = ContextCompat.getColorStateList(
                        root.context,
                        R.color.status_active
                    )
                } else {
                    userStatusChip.text = "Inactivo"
                    userStatusChip.chipBackgroundColor = ContextCompat.getColorStateList(
                        root.context,
                        R.color.status_inactive
                    )
                }

                // Imagen de perfil
                if (!user.foto.isNullOrEmpty()) {
                    Glide.with(root.context)
                        .load(user.foto)
                        .placeholder(R.drawable.ic_person_placeholder)
                        .error(R.drawable.ic_person_placeholder)
                        .circleCrop()
                        .into(userImageView)
                } else {
                    userImageView.setImageResource(R.drawable.ic_person_placeholder)
                }

                // Click en toda la tarjeta para editar
                root.setOnClickListener {
                    listener.onEditClick(user)
                }

                // Menú de opciones
                menuButton.setOnClickListener { view ->
                    showPopupMenu(view, user)
                }
            }
        }

        private fun showPopupMenu(view: View, user: UserListItem) {
            val popup = PopupMenu(view.context, view)
            popup.menuInflater.inflate(R.menu.menu_user_options, popup.menu)

            // Configurar visibilidad y texto según el estado
            popup.menu.findItem(R.id.action_toggle_status)?.apply {
                title = if (user.activo) "Desactivar cuenta" else "Activar cuenta"
                setIcon(if (user.activo) R.drawable.ic_toggle_off else R.drawable.ic_check)
            }

            // Mostrar WhatsApp solo si tiene teléfono
            popup.menu.findItem(R.id.action_whatsapp)?.isVisible =
                !user.telefono.isNullOrEmpty()

            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_view_profile -> {
                        listener.onViewProfileClick(user)
                        true
                    }
                    R.id.action_edit -> {
                        listener.onEditClick(user)
                        true
                    }
                    R.id.action_change_password -> {
                        listener.onChangePasswordClick(user)
                        true
                    }
                    R.id.action_toggle_status -> {
                        listener.onToggleStatusClick(user)
                        true
                    }
                    R.id.action_whatsapp -> {
                        listener.onWhatsAppClick(user)
                        true
                    }
                    R.id.action_delete -> {
                        listener.onDeleteClick(user)
                        true
                    }
                    else -> false
                }
            }

            popup.show()
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