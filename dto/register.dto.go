package dto


type RegisterRequest struct {
    NombresApellidos string `json:"nombres_apellidos" binding:"required"`
    FechaNacimiento string `json:"fecha_nacimiento" binding:"required"`
    TipoDocumento   string `json:"tipo_documento" binding:"required"`
    NumDocumento    string `json:"num_documento"`
    Sexo            string `json:"sexo" binding:"required"`
    Telefono        string `json:"telefono" binding:"required"`
    Correo          string `json:"correo" binding:"required,email"`
    RoleIDs         []uint `json:"role_ids" binding:"required"`
}

