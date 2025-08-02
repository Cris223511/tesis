package com.example.serious_game_usil.utils


import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.serious_game_usil.R
import com.example.serious_game_usil.data.Role
import com.example.serious_game_usil.databinding.ItemRolesBinding





class RolesAdapter(
    private val listener: OnRoleActionListener
) : ListAdapter<Role, RolesAdapter.RoleViewHolder>(RoleDiffCallback()) {

    interface OnRoleActionListener {
        fun onEditClick(role: Role)
        fun onDeleteClick(role: Role)
        fun onViewPermissionsClick(role: Role)
        fun onViewUsersClick(role: Role)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoleViewHolder {
        val binding = ItemRolesBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return RoleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RoleViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class RoleViewHolder(
        private val binding: ItemRolesBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(role: Role) {
            binding.apply {
                roleNameTextView.text = role.name

                // Determinar si es rol del sistema basado en el nombre
                val isSystemRole = role.name.lowercase() in listOf("administrador", "estudiante", "docente", "admin")

                // Mostrar descripción basada en el nombre del rol
                roleDescriptionTextView.text = when (role.name.lowercase()) {
                    "administrador", "admin" -> "Acceso completo al sistema"
                    "estudiante" -> "Acceso a cursos y evaluaciones"
                    "docente" -> "Gestión de cursos y calificaciones"
                    else -> "Rol personalizado"
                }

                // Por ahora mostrar 0 usuarios (se puede actualizar cuando tengas esa info)
                userCountTextView.text = "0 usuarios"

                if (isSystemRole) {
                    systemRoleChip.visibility = View.VISIBLE
                    roleNameTextView.setTextColor(
                        ContextCompat.getColor(root.context, R.color.primary)
                    )
                } else {
                    systemRoleChip.visibility = View.GONE
                    roleNameTextView.setTextColor(
                        ContextCompat.getColor(root.context, R.color.text_primary)
                    )
                }

                root.setOnClickListener {
                    listener.onViewPermissionsClick(role)
                }

                menuButton.setOnClickListener { view ->
                    showPopupMenu(view, role, isSystemRole)
                }
            }
        }

        private fun showPopupMenu(view: View, role: Role, isSystemRole: Boolean) {
            val popup = PopupMenu(view.context, view)
            popup.menuInflater.inflate(R.menu.menu_roles_options, popup.menu)

            if (isSystemRole) {
                popup.menu.findItem(R.id.action_delete)?.isEnabled = false
            }

            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_edit -> {
                        listener.onEditClick(role)
                        true
                    }
                    R.id.action_view_permissions -> {
                        listener.onViewPermissionsClick(role)
                        true
                    }
                    R.id.action_view_users -> {
                        listener.onViewUsersClick(role)
                        true
                    }
                    R.id.action_delete -> {
                        listener.onDeleteClick(role)
                        true
                    }
                    else -> false
                }
            }

            popup.show()
        }
    }

    class RoleDiffCallback : DiffUtil.ItemCallback<Role>() {
        override fun areItemsTheSame(oldItem: Role, newItem: Role): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Role, newItem: Role): Boolean {
            return oldItem == newItem
        }
    }
}